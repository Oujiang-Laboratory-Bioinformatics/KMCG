#include "KmerDistributionAnalyzer.h"
#include <numeric>
#include <stdexcept>
#include <iostream>
#include <cmath>
#include <algorithm>
#include <limits>
#include <iomanip>

constexpr double kPi = 3.14159265358979323846;

// --- Constructor ---
KmerDistributionAnalyzer::KmerDistributionAnalyzer(const std::vector<std::vector<uint64_t>>& kmer_counts)
    : m_kmer_counts(kmer_counts) 
{
}

// --- Helper: Gaussian Probability Density Function ---
double KmerDistributionAnalyzer::normalPDF(double x, double mean, double sigma) {
    if (sigma <= 1e-9) return 0.0; // Avoid division by zero
    return (1.0 / (sigma * std::sqrt(2.0 * kPi))) * std::exp(-0.5 * std::pow((x - mean) / sigma, 2));
}

// --- Helper: Robust Peak Detection (C++ implementation of scipy.signal logic) ---
void KmerDistributionAnalyzer::estimateInitialParametersRobust(
    const std::vector<double>& data, 
    double& out_mean, 
    double& out_sigma) 
{
    if (data.empty()) return;

    struct PeakCandidate {
        size_t idx;
        double height;
        double fwhm;
        double score; // Energy = Height * FWHM
    };
    std::vector<PeakCandidate> candidates;
    size_t n = data.size();

    // 1. Find Local Maxima
    for (size_t i = 1; i < n - 1; ++i) {
        // Check for local peak (strict > prev, >= next for flat tops)
        // Filter noise: must be > 0
        if (data[i] > data[i-1] && data[i] >= data[i+1] && data[i] > 1e-9) {
            
            double peak_val = data[i];
            double half_max = peak_val * 0.5;

            // 2. Search Left (Linear Interpolation)
            double l_pos = static_cast<double>(i);
            for (int j = static_cast<int>(i); j >= 0; --j) {
                if (data[j] <= half_max) {
                    if (j < static_cast<int>(n) - 1) {
                        double y1 = data[j];
                        double y2 = data[j+1];
                        double slope = y2 - y1;
                        if (std::abs(slope) > 1e-12) {
                            l_pos = static_cast<double>(j) + (half_max - y1) / slope;
                        } else {
                            l_pos = static_cast<double>(j);
                        }
                    }
                    break;
                }
            }

            // 3. Search Right (Linear Interpolation)
            double r_pos = static_cast<double>(i);
            for (size_t j = i; j < n; ++j) {
                if (data[j] <= half_max) {
                    if (j > 0) {
                        double y1 = data[j];
                        double y2 = data[j-1];
                        double slope = y2 - y1; // magnitude
                        if (std::abs(slope) > 1e-12) {
                            r_pos = static_cast<double>(j) - (half_max - y1) / slope;
                        } else {
                            r_pos = static_cast<double>(j);
                        }
                    }
                    break;
                }
            }

            double width = r_pos - l_pos;
            // Filter single-point spikes
            if (width >= 0.5) {
                candidates.push_back({ i, peak_val, width, peak_val * width });
            }
        }
    }

    // 4. Select Best Peak by Energy
    if (candidates.empty()) {
        // Fallback to simple max
        auto max_iter = std::max_element(data.begin(), data.end());
        out_mean = static_cast<double>(std::distance(data.begin(), max_iter));
        out_sigma = 1.0;
        return;
    }

    auto best_it = std::max_element(candidates.begin(), candidates.end(), 
        [](const PeakCandidate& a, const PeakCandidate& b) {
            return a.score < b.score;
        });

    out_mean = static_cast<double>(best_it->idx);
    out_sigma = best_it->fwhm / kFWHM_To_Sigma_Factor;

    // 5. Heuristic Safety Check
    if (out_mean > 0 && out_sigma > (out_mean * 0.8)) {
        std::cout << "  [Info] Peak detected is extremely broad. Using Poisson fallback." << std::endl;
        out_sigma = std::sqrt(out_mean);
    }
}

// --- Helper: Residual Sum of Squares (RSS) ---
double KmerDistributionAnalyzer::calculateRSS(
    const std::vector<double>& data, double mean, double sigma, size_t left, size_t right) 
{
    double rss = 0.0;
    for (size_t i = left; i <= right; ++i) {
        double actual_prob = data[i];
        double model_prob = normalPDF(static_cast<double>(i), mean, sigma);
        double diff = actual_prob - model_prob;
        rss += diff * diff;
    }
    return rss;
}

// --- Phase 0: Initialize & Normalize ---
void KmerDistributionAnalyzer::initialize() {
    if (m_kmer_counts.size() <= 1) throw std::runtime_error("Insufficient data rows.");
    
    size_t num_rows = m_kmer_counts.size();
    size_t num_cols = m_kmer_counts[0].size();
    
    m_normalized_distributions.resize(num_rows, std::vector<double>(num_cols, 0.0));
    m_fitted_models.resize(num_rows, std::vector<double>(num_cols, 0.0));

    for (size_t i = 0; i < num_rows; ++i) {
        uint64_t sum = 0;
        for (auto val : m_kmer_counts[i]) sum += val;
        
        if (sum > 0) {
            for (size_t j = 0; j < num_cols; ++j) {
                m_normalized_distributions[i][j] = static_cast<double>(m_kmer_counts[i][j]) / static_cast<double>(sum);
            }
        }
    }
}

// --- Phase 1: Fit Base Parameters (Adaptive Coordinate Descent) ---
void KmerDistributionAnalyzer::fitBaseParameters() {
    // 1. Identify Base Row (Max Signal)
    m_base_row_index = 1;
    uint64_t max_count = 0;
    for (size_t i = 1; i < m_kmer_counts.size(); ++i) {
        uint64_t sum = 0;
        for (auto val : m_kmer_counts[i]) sum += val;
        if (sum > max_count) {
            max_count = sum;
            m_base_row_index = i;
        }
    }
    
    std::cout << "Base Row Identified: Row " << m_base_row_index 
              << " (Total K-mers: " << max_count << ")" << std::endl;

    const auto& base_dist = m_normalized_distributions[m_base_row_index];

    // 2. Estimate Init Parameters (Robust FWHM)
    double current_mu = 0.0;
    double current_sigma = 0.0;
    estimateInitialParametersRobust(base_dist, current_mu, current_sigma);
    
    std::cout << "  > Initialization (Robust Peak): Mu ~ " << std::fixed << std::setprecision(4) << current_mu 
              << ", Sigma ~ " << current_sigma << std::endl;

    // 3. Refine using Adaptive Coordinate Descent
    double step_mu = INITIAL_STEP_MU;
    double step_sigma = INITIAL_STEP_SIGMA;
    
    // Use kSignalRetentionZScore for fitting window
    auto get_window = [&](double mu, double sigma) {
        size_t start = static_cast<size_t>(std::max(0.0, mu - kSignalRetentionZScore * sigma));
        size_t end = static_cast<size_t>(std::min((double)base_dist.size() - 1, mu + kSignalRetentionZScore * sigma));
        return std::make_pair(start, end);
    };

    double current_rss = std::numeric_limits<double>::max();

    // Initial RSS
    {
        auto w = get_window(current_mu, current_sigma);
        current_rss = calculateRSS(base_dist, current_mu, current_sigma, w.first, w.second);
    }

    for (int iter = 0; iter < MAX_ITERATIONS; ++iter) {
        bool improved = false;

        // --- Optimize Mu ---
        double mu_left = current_mu - step_mu;
        auto w_left = get_window(mu_left, current_sigma);
        double rss_left = calculateRSS(base_dist, mu_left, current_sigma, w_left.first, w_left.second);

        if (rss_left < current_rss) {
            current_mu = mu_left;
            current_rss = rss_left;
            improved = true;
        } else {
            double mu_right = current_mu + step_mu;
            auto w_right = get_window(mu_right, current_sigma);
            double rss_right = calculateRSS(base_dist, mu_right, current_sigma, w_right.first, w_right.second);
            if (rss_right < current_rss) {
                current_mu = mu_right;
                current_rss = rss_right;
                improved = true;
            }
        }

        // --- Optimize Sigma ---
        double sigma_down = current_sigma - step_sigma;
        if (sigma_down > 0.01) {
            auto w_down = get_window(current_mu, sigma_down);
            double rss_down = calculateRSS(base_dist, current_mu, sigma_down, w_down.first, w_down.second);
            if (rss_down < current_rss) {
                current_sigma = sigma_down;
                current_rss = rss_down;
                improved = true;
            } else {
                double sigma_up = current_sigma + step_sigma;
                auto w_up = get_window(current_mu, sigma_up);
                double rss_up = calculateRSS(base_dist, current_mu, sigma_up, w_up.first, w_up.second);
                if (rss_up < current_rss) {
                    current_sigma = sigma_up;
                    current_rss = rss_up;
                    improved = true;
                }
            }
        }

        // --- Adaptive Step ---
        if (!improved) {
            step_mu *= STEP_DECAY;
            step_sigma *= STEP_DECAY;
            if (step_mu < CONVERGENCE_TOL && step_sigma < CONVERGENCE_TOL) {
                break; // Converged
            }
        }
    }

    m_base_mu = current_mu;
    m_base_sigma = current_sigma;

    std::cout << "  > Refined Parameters (Adaptive RSS): Mu = " << m_base_mu 
              << ", Sigma = " << m_base_sigma 
              << " (Final RSS: " << std::scientific << current_rss << ")" << std::defaultfloat << std::endl;
}

// --- Phase 2: Model Projection ---
void KmerDistributionAnalyzer::projectModelToAllRows() {
    m_per_row_results.clear();
    m_total_kmer_count_group = 0.0;
    m_total_fitted_kmer_count_group = 0.0;

    for (size_t i = 1; i < m_kmer_counts.size(); ++i) {
        uint64_t row_sum = 0;
        for (auto val : m_kmer_counts[i]) row_sum += val;

        // Scientific Model: Normal Scaling
        double mu_i = m_base_mu;
        double scaling_factor = std::sqrt(static_cast<double>(m_base_row_index) / static_cast<double>(i));
        double sigma_i = m_base_sigma * scaling_factor;

        double fitted_sum = 0.0;
        double fitted_pct = 0.0;

        if (row_sum > 0) {
            // Generate Model (Probability Conservation)
            for (size_t k = 0; k < m_kmer_counts[i].size(); ++k) {
                double pdf = normalPDF(static_cast<double>(k), mu_i, sigma_i);
                m_fitted_models[i][k] = pdf * static_cast<double>(row_sum);
            }
            
            // Overlap
            fitted_sum = calculateOverlapSum(m_fitted_models[i], m_kmer_counts[i]);
            fitted_pct = (fitted_sum / static_cast<double>(row_sum)) * 100.0;
        }

        m_total_kmer_count_group += static_cast<double>(row_sum);
        m_total_fitted_kmer_count_group += fitted_sum;

        // Km Score
        double km = 0.0;
        if (row_sum > 0) {
            double ratio = fitted_sum / static_cast<double>(row_sum);
            km = (std::abs(ratio - 1.0) < 1e-9) ? 999999.0 : (1.0 / (1.0 - ratio));
        }

        m_per_row_results.push_back({
            i, mu_i, sigma_i, row_sum, fitted_sum, fitted_pct, km
        });
    }
}

// --- Phase 3: Final Metrics (Row 0 Truncation with Constant) ---
void KmerDistributionAnalyzer::calculateFinalMetric() {
    uint64_t row0_sum = 0;
    uint64_t row0_ignored_sum = 0;
    double cutoff_val = 0.0;
    size_t start_idx = 0;

    // 1. Calculate Truncation Logic based on Base Parameters and Configured Z-Score
    // Threshold = mu - kSignalRetentionZScore * sigma
    cutoff_val = m_base_mu - kSignalRetentionZScore * m_base_sigma;
    
    // Safety check: index must be >= 0
    if (cutoff_val > 0) {
        start_idx = static_cast<size_t>(cutoff_val); 
    } else {
        start_idx = 0;
    }

    if (!m_kmer_counts.empty()) {
        const auto& row0 = m_kmer_counts[0];

        if (start_idx < row0.size()) {
            // Warning: truncation index value is 0
            if (row0[start_idx] == 0) {
                std::cout << "  [Warning] Row 0 truncation index (" << start_idx 
                          << ") has a count of 0! " << std::endl;
            }
        }
        
        if (start_idx >= row0.size()) {
            // Threshold is beyond the data size, keep nothing
            row0_sum = 0;
            for(auto v : row0) row0_ignored_sum += v;
        } else {
            // Sum ignored part (0 to start_idx - 1)
            for (size_t k = 0; k < start_idx; ++k) {
                row0_ignored_sum += row0[k];
            }
            // Sum kept part (start_idx to end)
            for (size_t k = start_idx; k < row0.size(); ++k) {
                row0_sum += row0[k];
            }
        }
        
        std::cout << "[Analysis] Row 0 Truncation Applied:" << std::endl;
        std::cout << "  > Z-Score Param:       " << kSignalRetentionZScore << " Sigma" << std::endl;
        std::cout << "  > Threshold (Mu - Z*S): " << std::fixed << std::setprecision(2) << cutoff_val << std::endl;
        std::cout << "  > Start Index (Inc):    " << start_idx << std::endl;
        std::cout << "  > Ignored Kmer:      " << std::setprecision(0) << row0_ignored_sum << std::endl;
        std::cout << "  > Kept kmer:        " << row0_sum << std::endl;
    }

    // 2. Calculate Final Global Totals
    m_final_total_kmer_count = m_total_kmer_count_group + static_cast<double>(row0_sum);
    m_final_fitted_kmer_count = m_total_fitted_kmer_count_group;

    // 3. Calculate Global Km
    if (m_final_total_kmer_count == 0) {
        m_goodness_of_fit_metric = 0.0;
    } else {
        double ratio = m_final_fitted_kmer_count / m_final_total_kmer_count;
        m_goodness_of_fit_metric = (std::abs(ratio - 1.0) < 1e-9) ? 999999.0 : (1.0 / (1.0 - ratio));
    }

    // Sort results
    std::sort(m_per_row_results.begin(), m_per_row_results.end(), 
        [](const PerRowAnalysisResult& a, const PerRowAnalysisResult& b) {
            return a.row_index < b.row_index;
    });
}

double KmerDistributionAnalyzer::calculateOverlapSum(const std::vector<double>& model, const std::vector<uint64_t>& actual) {
    double sum = 0.0;
    size_t len = std::min(model.size(), actual.size());
    for (size_t j = 0; j < len; ++j) {
        sum += std::min(model[j], static_cast<double>(actual[j]));
    }
    return sum;
}

void KmerDistributionAnalyzer::runAnalysis() {
    initialize();
    fitBaseParameters();
    projectModelToAllRows();
    calculateFinalMetric();
}
