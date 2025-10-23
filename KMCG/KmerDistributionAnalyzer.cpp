#include "KmerDistributionAnalyzer.h"
#include <numeric>
#include <stdexcept>
#include <iostream>
#include <cmath>
#include <tuple>
#include <limits>
#include <algorithm>
#include <vector>

constexpr double kPi = 3.14159265358979323846;

namespace {

void normalizeData(const std::vector<int>& data_row, std::vector<double>& normalized_row) {
    long long row_sum = std::accumulate(data_row.begin(), data_row.end(), 0LL);
    if (row_sum != 0) {
        for (size_t i = 0; i < data_row.size(); ++i) {
            normalized_row[i] = static_cast<double>(data_row[i]) / row_sum;
        }
    } else {
        std::copy(data_row.begin(), data_row.end(), normalized_row.begin());
        std::cerr << "Warning: The total sum of a certain row is ZERO." << std::endl;
    }
}

size_t findInitialMean(const std::vector<double>& line) {
    double total_sum = std::accumulate(line.begin(), line.end(), 0.0);
    if (total_sum == 0.0) {
        return 0;
    }
    double target_sum = total_sum * 0.5;

    std::vector<double> prefix_sum(line.size() + 1, 0.0);
    std::partial_sum(line.begin(), line.end(), prefix_sum.begin() + 1);

    for (size_t window_size = 1; window_size <= line.size(); ++window_size) {
        for (size_t i = 0; i <= line.size() - window_size; ++i) {
            double current_window_sum = prefix_sum[i + window_size] - prefix_sum[i];
            if (current_window_sum >= target_sum) {
                auto max_iter = std::max_element(line.begin() + i, line.begin() + i + window_size);
                return std::distance(line.begin(), max_iter);
            }
        }
    }
    
    return std::distance(line.begin(), std::max_element(line.begin(), line.end()));
}

std::pair<size_t, size_t> findPeakWindow(const std::vector<double>& line, double percentage) {
    if (line.empty()) return {0, 0};
    double max_value = *std::max_element(line.begin(), line.end());
    double threshold = max_value * percentage;
    size_t best_left = 0, best_right = 0;
    double best_window_max = -1.0;

    size_t left = 0;
    while (left < line.size()) {
        if (line[left] >= threshold) {
            size_t right = left;
            double current_window_max = line[left];
            while (right + 1 < line.size() && line[right + 1] >= threshold) {
                right++;
                current_window_max = std::max(current_window_max, line[right]);
            }
            
            if (current_window_max > best_window_max ||
                (current_window_max == best_window_max && (right - left > best_right - best_left))) {
                best_left = left;
                best_right = right;
                best_window_max = current_window_max;
            }
            left = right + 1;
        } else {
            left++;
        }
    }
    return std::make_pair(best_left, best_right);
}

double calculateOverlapPercentage(
    const std::vector<double>& original_vector,
    const std::vector<double>& gaussian_vector,
    size_t left,
    size_t right
) {
    double sum_smaller_values = 0.0;
    double sum_bigger_values = 0.0;
    for (size_t i = left; i <= right; ++i) {
        sum_bigger_values += std::max(gaussian_vector[i], original_vector[i]);
    }
    for (size_t i = left; i <= right; ++i) {
        sum_smaller_values += std::min(gaussian_vector[i], original_vector[i]);
    }
    
    if (sum_bigger_values == 0.0) {
        return (sum_smaller_values == 0.0) ? 100.0 : 0.0;
    }
    return (sum_smaller_values / sum_bigger_values) * 100;
}

} // End anonymous namespace

// --- KmerDistributionAnalyzer Member Function Implementations ---

double KmerDistributionAnalyzer::normalPDF(double x, double mean, double stddev) {
    if (stddev <= 0) {
        return (x == mean) ? std::numeric_limits<double>::infinity() : 0.0;
    }
    double coefficient = 1.0 / (stddev * sqrt(2 * kPi));
    double exponent = exp(-0.5 * pow((x - mean) / stddev, 2));
    return coefficient * exponent;
}

double KmerDistributionAnalyzer::calculateErrorInRange(
    const std::vector<double>& data,
    double mean,
    double sigma,
    size_t left,
    size_t right
) {
    if (data.empty()) {
        throw std::invalid_argument("Data vector is empty.");
    }
    if (left > right || right >= data.size()) {
        throw std::invalid_argument("Invalid range.");
    }
    double sum_squared_error = 0.0;
    for (size_t i = left; i <= right; ++i) {
        double value = data[i];
        double pdf_value = normalPDF(static_cast<double>(i), mean, sigma);
        double error = value - pdf_value;
        sum_squared_error += error * error;
    }
    return sum_squared_error;
}

std::tuple<double, double> KmerDistributionAnalyzer::findInitialSigma(
    const std::vector<double>& data,
    double initial_mean,
    size_t left,
    size_t right,
    double sigma_min,
    double sigma_max,
    double sigma_step
) {
    if (data.empty()) {
        throw std::invalid_argument("Data vector is empty.");
    }
    if (sigma_min <= 0 || sigma_min >= sigma_max || sigma_step <= 0) {
        throw std::invalid_argument("Invalid sigma range or step.");
    }

    double best_sigma = sigma_min;
    double min_error = std::numeric_limits<double>::max();

    for (double sigma = sigma_min; sigma <= sigma_max; sigma += sigma_step) {
        double error = calculateErrorInRange(data, initial_mean, sigma, left, right);
        if (error < min_error) {
            min_error = error;
            best_sigma = sigma;
        }
    }
    return std::make_tuple(best_sigma, min_error);
}

std::tuple<double, double, double> KmerDistributionAnalyzer::refineMeanAndSigma(
    const std::vector<double>& data,
    double initial_mean,
    double initial_sigma,
    size_t left,
    size_t right,
    double mean_step,
    double sigma_step
) {
    if (data.empty()) {
        throw std::invalid_argument("Data vector is empty.");
    }
    if (left > right || right >= data.size()) {
        throw std::invalid_argument("Invalid range.");
    }
    
    double best_mean = initial_mean;
    double best_sigma = initial_sigma;
    double best_error = std::numeric_limits<double>::max();

    double mean_min = initial_mean * (1.0 - 0.05);
    double mean_max = initial_mean * (1.0 + 0.05);
    double sigma_min = std::max(0.01, initial_sigma * (1.0 - 0.5));
    double sigma_max = initial_sigma * (1.0 + 0.5);

    for (double mean = mean_min; mean <= mean_max; mean += mean_step) {
        for (double sigma = sigma_min; sigma <= sigma_max; sigma += sigma_step) {
            double error = calculateErrorInRange(data, mean, sigma, left, right);
            if (error < best_error) {
                best_mean = mean;
                best_sigma = sigma;
                best_error = error;
            }
        }
    }
    return std::make_tuple(best_mean, best_sigma, best_error);
}

std::tuple<double, double, double> KmerDistributionAnalyzer::finalizeParametersForMaxOverlap(
    const std::vector<double>& original_vector,
    std::vector<double>& gaussian_vector,
    double best_mean,
    double best_sigma,
    double row_sum,
    size_t left,
    size_t right
) {
    double sigma_min = best_sigma * 0.9;
    double sigma_max = best_sigma;
    double final_sigma = best_sigma;
    double final_scaling_factor = 1.0;
    double best_percentage = 0.0;

    for (double sigma = sigma_max; sigma >= sigma_min; sigma -= SCALING_FACTOR_STEP) {
        for (double A = SCALING_FACTOR_MAX; A >= SCALING_FACTOR_MIN; A -= SCALING_FACTOR_STEP) {
            double adjusted_row_sum = row_sum * A;
            
            for (size_t i = 0; i < gaussian_vector.size(); ++i) {
                gaussian_vector[i] = normalPDF(static_cast<double>(i), best_mean, sigma) * adjusted_row_sum;
            }
            
            double percentage = calculateOverlapPercentage(original_vector, gaussian_vector, left, right);
            if (percentage > best_percentage) {
                best_percentage = percentage;
                final_sigma = sigma;
                final_scaling_factor = A;
            }
        }
    }
    return std::make_tuple(final_sigma, final_scaling_factor, best_percentage);
}

KmerDistributionAnalyzer::KmerDistributionAnalyzer(const std::vector<std::vector<int>>& kmer_counts)
    : m_kmer_counts(kmer_counts)
{}

void KmerDistributionAnalyzer::initialize() {
    if (m_kmer_counts.size() <= 1) {
        throw std::out_of_range("At least two rows of data are required for analysis.");
    }
    const size_t num_rows = m_kmer_counts.size();

    if (m_kmer_counts[0].empty()) {
        throw std::runtime_error("A certain row in the data is entirely empty.");
    }

    const size_t num_cols = m_kmer_counts[0].size();
    
    for (size_t i = 1; i < num_rows; ++i) {
        if (m_kmer_counts[i].size() != num_cols) {
            throw std::runtime_error(
                "Data rows are not uniform (ragged array). Row 0 has " + 
                std::to_string(num_cols) + " columns, but row " + 
                std::to_string(i) + " has " + 
                std::to_string(m_kmer_counts[i].size()) + " columns."
            );
        }
    }

    m_normalized_distributions.resize(num_rows, std::vector<double>(num_cols, 0.0));
    m_fitted_gaussian_models.resize(num_rows, std::vector<double>(num_cols, 0.0));

    for (size_t i = 0; i < num_rows; ++i) {
        normalizeData(m_kmer_counts[i], m_normalized_distributions[i]);
    }
}

void KmerDistributionAnalyzer::bootstrapModelParameters() {
    const size_t bootstrap_row_idx = 1;
    const auto& data_row = m_kmer_counts[bootstrap_row_idx];
    const auto& normalized_row = m_normalized_distributions[bootstrap_row_idx];

    double row_sum = std::accumulate(data_row.begin(), data_row.end(), 0.0);
    if (row_sum <= 0) {
        throw std::runtime_error("The total sum of data in the No.1 row is zero or negative.");
    }

    auto window = findPeakWindow(normalized_row, PEAK_WINDOW_THRESHOLD);
    size_t left = window.first;
    size_t right = window.second;

    double init_mean = findInitialMean(normalized_row);
    
    double init_sigma, sigma_error;
    std::tie(init_sigma, sigma_error) = findInitialSigma(normalized_row, init_mean, left, right, SIGMA_GRID_SEARCH_MIN, SIGMA_GRID_SEARCH_MAX, SIGMA_GRID_SEARCH_STEP);
    
    double best_mean, best_sigma, min_error;
    std::tie(best_mean, best_sigma, min_error) = refineMeanAndSigma(normalized_row, init_mean, init_sigma, left, right, MEAN_OPTIMIZE_STEP, SIGMA_OPTIMIZE_STEP);

    m_final_mean = best_mean;
    m_mean_search_radius = std::min(best_mean - left, right - best_mean);

    std::vector<double> temp_model(normalized_row.size(), 0.0);
    std::vector<double> data_row_double(data_row.begin(), data_row.end());

    double final_sigma, scaling_factor, opt_percentage;
    std::tie(final_sigma, scaling_factor, opt_percentage) = finalizeParametersForMaxOverlap(
        data_row_double, temp_model, best_mean, best_sigma, 
        row_sum, left, right
    );

    for (size_t j = 0; j < normalized_row.size(); ++j) {
        m_fitted_gaussian_models[bootstrap_row_idx][j] = std::ceil(normalPDF(static_cast<double>(j), best_mean, final_sigma) * row_sum * scaling_factor);
    }
        
    for (size_t j = 0; j < normalized_row.size(); ++j) {
        double actual = static_cast<double>(data_row[j]);
        double model = m_fitted_gaussian_models[bootstrap_row_idx][j];

        if (actual > model) {
            double excess = actual - model;

            double distance_from_mean = std::abs(static_cast<double>(j) - best_mean);
            double penalty_weight = 1.0 + distance_from_mean;

            m_total_weighted_unfitted_sum += (excess * penalty_weight) / final_sigma;
        }
    }
    
    double overlap_for_percentage = calculateOverlapSum(m_fitted_gaussian_models[bootstrap_row_idx], data_row);
    m_total_kmer_count += row_sum;
    m_overlap_percentages.push_back((overlap_for_percentage / row_sum) * 100.0);

    m_base_sigma = final_sigma;
}

void KmerDistributionAnalyzer::estimateSubsequentRowMeans() {
    double prev_mean = m_final_mean;

    for (size_t i = 2; i < m_kmer_counts.size(); ++i) {
        const auto& current_row_counts = m_kmer_counts[i];
        const size_t num_cols = current_row_counts.size();
        
        size_t left = static_cast<size_t>(std::max(0.0, std::floor(prev_mean - (m_mean_search_radius / sqrt(i)))));
        size_t right = static_cast<size_t>(std::min(static_cast<double>(num_cols - 1), std::floor(prev_mean + (m_mean_search_radius / sqrt(i))) + 1));
        
        if (left > right || right >= num_cols) {
             left = 0;
             right = (num_cols == 0) ? 0 : num_cols - 1;
        }

        double weighted_sum = 0.0;
        double data_sum_in_window = 0.0;
        for (size_t j = left; j <= right; ++j) {
            weighted_sum += j * current_row_counts[j];
            data_sum_in_window += current_row_counts[j];
        }

        double estimated_mean = (data_sum_in_window != 0.0) ? weighted_sum / data_sum_in_window : prev_mean;
        prev_mean = estimated_mean;

        double total_kmer_count = std::accumulate(current_row_counts.begin(), current_row_counts.end(), 0.0);
        m_intermediate_fit_results.push_back({i, estimated_mean, left, right, total_kmer_count});
        m_total_kmer_count += total_kmer_count;
    }
}

void KmerDistributionAnalyzer::averageRowMeans() {
    if (m_intermediate_fit_results.empty()) {
        return;
    }

    double sum_of_means = 0.0;
    for (const auto& result : m_intermediate_fit_results) {
        sum_of_means += result.estimated_mean;
    }
    
    m_final_mean = sum_of_means / m_intermediate_fit_results.size();
}

void KmerDistributionAnalyzer::fitModelToSubsequentRows() {
    for (const auto& result : m_intermediate_fit_results) {
        size_t i = result.row_index;
        
        if (result.total_kmer_count <= 0.0) {
            double last_percentage = m_overlap_percentages.empty() ? 0.0 : m_overlap_percentages.back();
            m_overlap_percentages.push_back(last_percentage);
            continue;
        }

        double sigma = m_base_sigma / sqrt(i);
        for (size_t j = 0; j < m_kmer_counts[i].size(); ++j) {
            m_fitted_gaussian_models[i][j] = std::ceil(normalPDF(static_cast<double>(j), m_final_mean, sigma) * result.total_kmer_count);
        }
        
        for (size_t j = 0; j < m_kmer_counts[i].size(); ++j) {
            double actual = static_cast<double>(m_kmer_counts[i][j]);
            double model = m_fitted_gaussian_models[i][j];

            if (actual > model) {
                double excess = actual - model;

                double distance_from_mean = std::abs(static_cast<double>(j) - m_final_mean);
                double penalty_weight = 1.0 + (distance_from_mean / sigma);

                m_total_weighted_unfitted_sum += (excess * penalty_weight);
            }
        }
        double overlap_for_percentage = calculateOverlapSum(m_fitted_gaussian_models[i], m_kmer_counts[i]);
        
        m_overlap_percentages.push_back((overlap_for_percentage / result.total_kmer_count) * 100.0);
    }
}

void KmerDistributionAnalyzer::calculateFinalMetric() {
    double row_0_sum = std::accumulate(m_kmer_counts[0].begin(), m_kmer_counts[0].end(), 0.0);

    const double ROW_0_PENALTY_WEIGHT = 1.0;
    double weighted_row_0_unfitted = row_0_sum * ROW_0_PENALTY_WEIGHT;

    double total_weighted_unfitted = weighted_row_0_unfitted + m_total_weighted_unfitted_sum;
    
    m_total_kmer_count += row_0_sum;

    if (m_total_kmer_count == 0) {
        throw std::runtime_error("Total k-mer count is zero. Cannot divide by zero.");
    }

    if (total_weighted_unfitted <= 1e-9) {
        m_goodness_of_fit_metric = std::numeric_limits<double>::infinity();
    } else {
        m_goodness_of_fit_metric = m_total_kmer_count / total_weighted_unfitted;
    }
}

double KmerDistributionAnalyzer::calculateOverlapSum(const std::vector<double>& model_values, const std::vector<int>& actual_values) {
    double sum = 0.0;
    size_t size = std::min(model_values.size(), actual_values.size());
    for (size_t j = 0; j < size; ++j) {
        sum += std::min(model_values[j], static_cast<double>(actual_values[j]));
    }
    return sum;
}


void KmerDistributionAnalyzer::runAnalysis() {
    m_total_weighted_unfitted_sum = 0.0;
    initialize();
    bootstrapModelParameters();
    estimateSubsequentRowMeans();
    averageRowMeans();
    fitModelToSubsequentRows();
    calculateFinalMetric();
}