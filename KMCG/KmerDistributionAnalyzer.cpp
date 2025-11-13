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


KmerDistributionAnalyzer::KmerDistributionAnalyzer(const std::vector<std::vector<int>>& kmer_counts, int num_bootstrap_rows)
    : m_kmer_counts(kmer_counts), m_num_bootstrap_rows(num_bootstrap_rows)
{
    if (m_num_bootstrap_rows < 1) {
        throw std::invalid_argument("Number of bootstrap rows must be at least 1.");
    }
}

void KmerDistributionAnalyzer::initialize() {
    // Check if there are enough rows for bootstrapping (n) + error row (1)
    if (m_kmer_counts.size() <= m_num_bootstrap_rows) {
        throw std::out_of_range(
            "At least " + std::to_string(m_num_bootstrap_rows + 1) +
            " rows of data are required for analysis (num_bootstrap_rows + 1)."
        );
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
    
    // Overlap percentages vector size is (total_rows - 1), as it excludes row 0
    m_overlap_percentages.resize(num_rows - 1, 0.0);
}

void KmerDistributionAnalyzer::bootstrapModelParameters() {
    // Loop 'n' times to bootstrap parameters
    for (int j = 0; j < m_num_bootstrap_rows; ++j) {
        const size_t bootstrap_row_idx = j + 1; // Use row 1, 2, ..., n
        const auto& data_row = m_kmer_counts[bootstrap_row_idx];
        const auto& normalized_row = m_normalized_distributions[bootstrap_row_idx];

        double row_sum = std::accumulate(data_row.begin(), data_row.end(), 0.0);
        if (row_sum <= 0) {
            throw std::runtime_error("The total sum of data in the No." + std::to_string(bootstrap_row_idx) + " row is zero or negative.");
        }

        auto window = findPeakWindow(normalized_row, PEAK_WINDOW_THRESHOLD);
        size_t left = window.first;
        size_t right = window.second;

        double init_mean = findInitialMean(normalized_row);
        
        double init_sigma, sigma_error;
        std::tie(init_sigma, sigma_error) = findInitialSigma(normalized_row, init_mean, left, right, SIGMA_GRID_SEARCH_MIN, SIGMA_GRID_SEARCH_MAX, SIGMA_GRID_SEARCH_STEP);
        
        double best_mean, best_sigma, min_error;
        std::tie(best_mean, best_sigma, min_error) = refineMeanAndSigma(normalized_row, init_mean, init_sigma, left, right, MEAN_OPTIMIZE_STEP, SIGMA_OPTIMIZE_STEP);

        std::vector<double> temp_model(normalized_row.size(), 0.0);
        std::vector<double> data_row_double(data_row.begin(), data_row.end());

        double final_sigma, scaling_factor, opt_percentage;
        std::tie(final_sigma, scaling_factor, opt_percentage) = finalizeParametersForMaxOverlap(
            data_row_double, temp_model, best_mean, best_sigma, 
            row_sum, left, right
        );

        // Check if this row has the max k-mer count so far
        if (row_sum > m_base_sigma_kmer_count) {
            m_base_sigma_kmer_count = row_sum;
            m_base_sigma = final_sigma;
            m_base_sigma_row_index = bootstrap_row_idx;
            // Use this mean/radius as the starting point for subsequent estimations
            m_final_mean = best_mean;
            m_mean_search_radius = std::min(best_mean - left, right - best_mean);
        }

        for (size_t k = 0; k < normalized_row.size(); ++k) {
            m_fitted_gaussian_models[bootstrap_row_idx][k] = std::ceil(normalPDF(static_cast<double>(k), best_mean, final_sigma) * row_sum * scaling_factor);
        }
            
        // Reverted to simple logic: Calculate fitted k-mer sum (overlap)
        double row_fitted_sum = calculateOverlapSum(m_fitted_gaussian_models[bootstrap_row_idx], data_row);
        
        // Add to the single group's total
        m_total_kmer_count_group += row_sum;
        m_total_fitted_kmer_count_group += row_fitted_sum;
        
        // Set percentage by index (row 1 is index 0 in the percentages vector)
        double fitted_percentage = (row_sum > 0) ? (row_fitted_sum / row_sum) * 100.0 : 0.0;
        m_overlap_percentages[bootstrap_row_idx - 1] = fitted_percentage;
        
        // Calculate PER-ROW Km as requested
        double per_row_km = 0.0;
        if (row_sum > 0) {
            double ratio = row_fitted_sum / row_sum;
            if (std::abs(ratio - 1.0) < 1e-9) {
                per_row_km = std::numeric_limits<double>::infinity();
            } else {
                per_row_km = 1.0 / (1.0 - ratio);
            }
        }
        
        m_per_row_results.push_back({
            bootstrap_row_idx,
            best_mean, // This row's estimated mean
            row_sum,
            row_fitted_sum,
            fitted_percentage,
            per_row_km // Store the per-row Km
        });
    }
}

void KmerDistributionAnalyzer::estimateSubsequentRowMeans() {
    // The starting 'prev_mean' is m_final_mean (set to the mean of the max-kmer bootstrap row)
    double prev_mean = m_final_mean; 

    // Start iterating from the first row *after* the bootstrap rows
    for (size_t i = m_num_bootstrap_rows + 1; i < m_kmer_counts.size(); ++i) {
        const auto& current_row_counts = m_kmer_counts[i];
        const size_t num_cols = current_row_counts.size();
        
        // Use the mean search radius from the max-kmer bootstrap row
        // Use the original shrink logic from your user-provided file
        double shrink_factor = sqrt(static_cast<double>(i)); 

        size_t left = static_cast<size_t>(std::max(0.0, std::floor(prev_mean - (m_mean_search_radius / shrink_factor))));
        size_t right = static_cast<size_t>(std::min(static_cast<double>(num_cols - 1), std::floor(prev_mean + (m_mean_search_radius / shrink_factor)) + 1));
        
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
        prev_mean = estimated_mean; // Update this group's "previous mean" for the next iteration

        double total_kmer_count = std::accumulate(current_row_counts.begin(), current_row_counts.end(), 0.0);
        
        // Add the result to the single intermediate vector
        m_intermediate_fit_results.push_back(
            {i, estimated_mean, left, right, total_kmer_count}
        );
    }
}

void KmerDistributionAnalyzer::averageRowMeans() {
    // Average the means from the *subsequent* rows (n+1 onward)
    if (m_intermediate_fit_results.empty()) {
        // No subsequent rows. The final mean remains the one
        // from the max-kmer bootstrap row.
        return;
    }

    double sum_of_means = 0.0;
    for (const auto& result : m_intermediate_fit_results) {
        sum_of_means += result.estimated_mean;
    }
    
    // Overwrite the bootstrap mean with the average of *only* the subsequent means.
    m_final_mean = sum_of_means / m_intermediate_fit_results.size();
}

void KmerDistributionAnalyzer::fitModelToSubsequentRows() {
    // Get the final, averaged mean for all subsequent rows
    double group_mean = m_final_mean;

    // Iterate over the intermediate results (rows n+1 onward)
    for (const auto& result : m_intermediate_fit_results) {
        size_t i = result.row_index; // The actual row index in m_kmer_counts
        double row_fitted_sum = 0.0;
        double fitted_percentage = 0.0;
        
        if (result.total_kmer_count <= 0.0) {
            // This row has no data. Copy the overlap percentage from the
            // previous row (which must be i-1).
            fitted_percentage = m_overlap_percentages.empty() ? 0.0 : m_overlap_percentages.back();
            m_overlap_percentages.push_back(fitted_percentage);
        } 
        else 
        {
            // sigma_i = (sigma_k * sqrt(k)) / sqrt(i)
            double sigma = (m_base_sigma * sqrt(static_cast<double>(m_base_sigma_row_index))) / sqrt(static_cast<double>(i));
            
            for (size_t k = 0; k < m_kmer_counts[i].size(); ++k) {
                m_fitted_gaussian_models[i][k] = std::ceil(normalPDF(static_cast<double>(k), group_mean, sigma) * result.total_kmer_count);
            }
            
            // Reverted to simple logic
            row_fitted_sum = calculateOverlapSum(m_fitted_gaussian_models[i], m_kmer_counts[i]);
            fitted_percentage = (result.total_kmer_count > 0) ? (row_fitted_sum / result.total_kmer_count) * 100.0 : 0.0;
            m_overlap_percentages.push_back(fitted_percentage);
        }
        
        // Add to the single group's totals
        m_total_kmer_count_group += result.total_kmer_count;
        m_total_fitted_kmer_count_group += row_fitted_sum;

        // Calculate PER-ROW Km as requested
        double per_row_km = 0.0;
        if (result.total_kmer_count > 0) { // Use result.total_kmer_count
            double ratio = row_fitted_sum / result.total_kmer_count;
            if (std::abs(ratio - 1.0) < 1e-9) {
                per_row_km = std::numeric_limits<double>::infinity();
            } else {
                per_row_km = 1.0 / (1.0 - ratio);
            }
        } else if (!m_per_row_results.empty()) {
             // If row is empty, copy the Km from the previous row
            per_row_km = m_per_row_results.back().per_row_km; 
        }

        m_per_row_results.push_back({
            i,
            result.estimated_mean, // This row's estimated mean
            result.total_kmer_count,
            row_fitted_sum,
            fitted_percentage,
            per_row_km // Store the per-row Km
        });
    }
}

void KmerDistributionAnalyzer::calculateFinalMetric() {
    double row_0_sum = std::accumulate(m_kmer_counts[0].begin(), m_kmer_counts[0].end(), 0.0);
    
    // The global total k-mer count starts with row 0
    m_total_kmer_count = row_0_sum;

    // Calculate the final Km for the single group
    // Total k-mers is the accumulated sum + row 0
    double total_kmer_count_for_group = m_total_kmer_count_group + row_0_sum;
    // Fitted k-mers is just the accumulated sum (row 0 is pure penalty)
    double total_fitted_count_for_group = m_total_fitted_kmer_count_group;
    
    // Store these final values for the getter
    m_final_total_kmer_count = total_kmer_count_for_group;
    m_final_fitted_kmer_count = total_fitted_count_for_group;

    // Add this group's count to the global total
    m_total_kmer_count += m_total_kmer_count_group; 

    if (total_kmer_count_for_group == 0) {
        m_goodness_of_fit_metric = 0.0; 
    } else {
        double ratio = total_fitted_count_for_group / total_kmer_count_for_group;
        
        if (std::abs(ratio - 1.0) < 1e-9) {
            m_goodness_of_fit_metric = std::numeric_limits<double>::infinity();
        } else {
            m_goodness_of_fit_metric = 1.0 / (1.0 - ratio);
        }
    }

    // Sort the detailed results by row index for clean printing
    std::sort(m_per_row_results.begin(), m_per_row_results.end(), 
        [](const PerRowAnalysisResult& a, const PerRowAnalysisResult& b) {
            return a.row_index < b.row_index;
    });
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
    // Reset all accumulators
    m_total_kmer_count_group = 0.0;
    m_total_fitted_kmer_count_group = 0.0;
    m_final_total_kmer_count = 0.0;
    m_final_fitted_kmer_count = 0.0;

    m_base_sigma_kmer_count = -1.0; // Reset to -1 to find max
    m_base_sigma = 0.0;
    m_base_sigma_row_index = 1;
    m_final_mean = 0.0;
    m_mean_search_radius = 0.0;

    m_intermediate_fit_results.clear();
    m_total_kmer_count = 0.0;
    m_per_row_results.clear(); // Clear the detailed log
    
    initialize();
    bootstrapModelParameters();
    estimateSubsequentRowMeans();
    averageRowMeans();
    fitModelToSubsequentRows();
    calculateFinalMetric();
}
