#include "KmerDistributionAnalyzer.h"
#include <numeric>
#include <stdexcept>
#include <iostream>
#include <cmath>
#include <algorithm>
#include <limits>
#include <iomanip>

// Mathematical constant for Gaussian calculations
constexpr double kPi = 3.14159265358979323846;

// ============================================================================
// Constructor
// ============================================================================
KmerDistributionAnalyzer::KmerDistributionAnalyzer(const std::vector<std::vector<uint64_t>>& kmer_counts)
    : m_kmer_counts(kmer_counts) 
{
}

// ============================================================================
// Mathematical Helpers
// ============================================================================

/**
 * @brief Computes the value of the Gaussian Probability Density Function (PDF).
 * @param x The observation point.
 * @param mean The distribution mean (mu).
 * @param sigma The standard deviation (sigma).
 * @return The probability density at x.
 */
double KmerDistributionAnalyzer::normalPDF(double x, double mean, double sigma) {
    if (sigma <= 1e-9) return 0.0; // Prevent singularity at sigma=0
    return (1.0 / (sigma * std::sqrt(2.0 * kPi))) * std::exp(-0.5 * std::pow((x - mean) / sigma, 2));
}

/**
 * @brief Estimates initial Gaussian parameters using a robust peak detection algorithm.
 * @details This method employs a signal processing approach to identify significant peaks
 * while ignoring minor fluctuations. It includes sub-pixel refinement via
 * linear interpolation for precise FWHM estimation.
 */
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
        double energy; // Energy = Height * FWHM (Proxy for peak significance)
    };
    std::vector<PeakCandidate> candidates;
    size_t n = data.size();

    // 1. Identification of Local Maxima
    //    Iterate through data to find points strictly greater than predecessors 
    //    and greater/equal to successors (handling flat tops).
    for (size_t i = 1; i < n - 1; ++i) {
        if (data[i] > data[i-1] && data[i] >= data[i+1] && data[i] > 1e-9) {
            
            double peak_val = data[i];
            double half_max = peak_val * 0.5;

            // 2. Full Width at Half Maximum (FWHM) Estimation
            //    A. Search Left (with linear interpolation for sub-index precision)
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

            //    B. Search Right
            double r_pos = static_cast<double>(i);
            for (size_t j = i; j < n; ++j) {
                if (data[j] <= half_max) {
                    if (j > 0) {
                        double y1 = data[j];
                        double y2 = data[j-1];
                        double slope = y2 - y1; // slope magnitude relative to direction
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
            
            // 3. Candidate Filtering
            //    Exclude narrow spikes (width < 0.5) which are likely sequencing artifacts.
            if (width >= 0.5) {
                candidates.push_back({ i, peak_val, width, peak_val * width });
            }
        }
    }

    // 4. Selection of the Optimal Peak
    if (candidates.empty()) {
        // Fallback: Global maximum index
        auto max_iter = std::max_element(data.begin(), data.end());
        out_mean = static_cast<double>(std::distance(data.begin(), max_iter));
        out_sigma = 1.0;
        return;
    }

    // Select candidate with the highest "Energy" (robust against high-frequency noise)
    auto best_it = std::max_element(candidates.begin(), candidates.end(), 
        [](const PeakCandidate& a, const PeakCandidate& b) {
            return a.energy < b.energy;
        });

    out_mean = static_cast<double>(best_it->idx);
    out_sigma = best_it->fwhm / kFWHM_To_Sigma_Factor;

    // 5. Heuristic Constraint Validation (Poisson Assumption Fallback)
    //    If the detected peak is suspiciously broad (Sigma > 0.8 * Mu), 
    //    it likely violates the properties of a Poisson/Gaussian mixture in K-mer spectra.
    //    We enforce a Poisson prior: Sigma ~ sqrt(Mu).
    if (out_mean > 0 && out_sigma > (out_mean * 0.8)) {
        std::cout << "  [Info] Peak detected is extremely broad. Applying Poisson prior (Sigma = sqrt(Mu))." << std::endl;
        out_sigma = std::sqrt(out_mean);
    }
}

/**
 * @brief Calculates the Residual Sum of Squares (RSS) for goodness-of-fit assessment.
 */
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

// ============================================================================
// Phase 0: Initialization
// ============================================================================
void KmerDistributionAnalyzer::initialize() {
    if (m_kmer_counts.size() <= 1) throw std::runtime_error("Insufficient data rows for analysis.");
    
    size_t num_rows = m_kmer_counts.size();
    size_t num_cols = m_kmer_counts[0].size();
    
    m_normalized_distributions.resize(num_rows, std::vector<double>(num_cols, 0.0));
    m_fitted_models.resize(num_rows, std::vector<double>(num_cols, 0.0));

    // Normalize each row to a probability distribution (Sum = 1.0)
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

// ============================================================================
// Phase 1: Base Parameter Fitting
// ============================================================================
void KmerDistributionAnalyzer::fitBaseParameters() {
    // 1. Identify the "Anchor Row" (The row with maximum signal/k-mer count)
    //    This row typically has the highest Signal-to-Noise Ratio (SNR).
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
    
    // Output: Explicitly state which row is being fitted
    std::cout << "-----------------------------------------------------------" << std::endl;
    std::cout << "[Step 1] Base Peak Fitting (Anchor Row: " << m_base_row_index 
              << ", Count: " << max_count << ")" << std::endl;

    const auto& base_dist = m_normalized_distributions[m_base_row_index];

    // 2. Initial Parameter Estimation (Robust Method)
    double current_mu = 0.0;
    double current_sigma = 0.0;
    estimateInitialParametersRobust(base_dist, current_mu, current_sigma);
    
    std::cout << "  > Init Guess [Row " << m_base_row_index << "]:"
              << " Mu ~ " << std::fixed << std::setprecision(2) << current_mu 
              << ", Sigma ~ " << current_sigma << std::endl;

    // 3. Parameter Refinement (Adaptive Coordinate Descent)
    //    Optimizes Mu and Sigma to minimize RSS within the Z-score window.
    double step_mu = INITIAL_STEP_MU;
    double step_sigma = INITIAL_STEP_SIGMA;
    
    auto get_window = [&](double mu, double sigma) {
        size_t start = static_cast<size_t>(std::max(0.0, mu - kSignalRetentionZScore * sigma));
        size_t end = static_cast<size_t>(std::min((double)base_dist.size() - 1, mu + kSignalRetentionZScore * sigma));
        return std::make_pair(start, end);
    };

    double current_rss = std::numeric_limits<double>::max();

    // Calculate baseline RSS
    {
        auto w = get_window(current_mu, current_sigma);
        current_rss = calculateRSS(base_dist, current_mu, current_sigma, w.first, w.second);
    }

    // Iterative Optimization Loop
    for (int iter = 0; iter < MAX_ITERATIONS; ++iter) {
        bool improved = false;

        // --- Optimize Mu (Mean) ---
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

        // --- Optimize Sigma (Standard Deviation) ---
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

        // --- Adaptive Learning Rate Decay ---
        if (!improved) {
            step_mu *= STEP_DECAY;
            step_sigma *= STEP_DECAY;
            if (step_mu < CONVERGENCE_TOL && step_sigma < CONVERGENCE_TOL) {
                break; // Convergence reached
            }
        }
    }

    m_base_mu = current_mu;
    m_base_sigma = current_sigma;

    // Output: Final optimized parameters for the anchor row
    std::cout << "  > Final Fit  [Row " << m_base_row_index << "]:"
              << " Mu = " << std::setw(7) << m_base_mu 
              << ", Sigma = " << std::setw(6) << m_base_sigma 
              << " (RSS: " << std::scientific << std::setprecision(2) << current_rss << ")" 
              << std::defaultfloat << std::endl;
}

// ============================================================================
// Phase 2: Model Projection
// ============================================================================
void KmerDistributionAnalyzer::projectModelToAllRows() {
    m_per_row_results.clear();
    m_total_kmer_count_group = 0.0;
    m_total_fitted_kmer_count_group = 0.0;

    for (size_t i = 1; i < m_kmer_counts.size(); ++i) {
        uint64_t row_sum = 0;
        for (auto val : m_kmer_counts[i]) row_sum += val;

        // Projection Logic: 
        // Assumes k-mer distribution scales with square root of coverage depth (Poisson property).
        // Scaling Factor = sqrt(Mu_base / Mu_current) ~= sqrt(Row_base / Row_current)
        double mu_i = m_base_mu; // In this model, we fix Mu (or could shift it), here we assume alignment.
        double scaling_factor = std::sqrt(static_cast<double>(m_base_row_index) / static_cast<double>(i));
        double sigma_i = m_base_sigma * scaling_factor;

        double fitted_sum = 0.0;
        double fitted_pct = 0.0;

        if (row_sum > 0) {
            // Reconstruct the Gaussian model for Row i
            for (size_t k = 0; k < m_kmer_counts[i].size(); ++k) {
                double pdf = normalPDF(static_cast<double>(k), mu_i, sigma_i);
                m_fitted_models[i][k] = pdf * static_cast<double>(row_sum);
            }
            
            // Calculate overlap coefficient
            fitted_sum = calculateOverlapSum(m_fitted_models[i], m_kmer_counts[i]);
            fitted_pct = (fitted_sum / static_cast<double>(row_sum)) * 100.0;
        }

        m_total_kmer_count_group += static_cast<double>(row_sum);
        m_total_fitted_kmer_count_group += fitted_sum;

        // Calculate 'Km' consistency score
        double km = 0.0;
        if (row_sum > 0) {
            double ratio = fitted_sum / static_cast<double>(row_sum);
            // Avoid division by zero in perfect fit scenario
            km = (std::abs(ratio - 1.0) < 1e-9) ? 999999.0 : (1.0 / (1.0 - ratio));
        }

        m_per_row_results.push_back({
            i, mu_i, sigma_i, row_sum, fitted_sum, fitted_pct, km
        });
    }
}

// ============================================================================
// Phase 3: Final Metric Calculation & Noise Truncation
// ============================================================================
void KmerDistributionAnalyzer::calculateFinalMetric() {
    uint64_t row0_sum = 0;
    uint64_t row0_ignored_sum = 0;
    double cutoff_val = 0.0;
    size_t start_idx = 0;

    // 1. Signal-to-Noise Separation Logic
    //    Determine the cutoff threshold based on the Anchor Row's parameters.
    //    Formula: Threshold = Mu - (Z-Score * Sigma)
    cutoff_val = m_base_mu - kSignalRetentionZScore * m_base_sigma;
    
    if (cutoff_val > 0) {
        start_idx = static_cast<size_t>(cutoff_val); 
    } else {
        start_idx = 0;
    }

    if (!m_kmer_counts.empty()) {
        const auto& row0 = m_kmer_counts[0];
        
        // [Safety Check]: Detect potential distribution gaps at the cutoff point
        if (start_idx < row0.size()) {
            if (row0[start_idx] == 0) {
                std::cout << "\n  [WARNING] Cutoff Index (" << start_idx 
                          << ") at Row 0 has ZERO count!" << std::endl;
            }
        }

        // Apply Truncation: Separate Noise (< start_idx) from Signal (>= start_idx)
        if (start_idx >= row0.size()) {
            // Cutoff exceeds data range; treat everything as noise.
            row0_sum = 0;
            for(auto v : row0) row0_ignored_sum += v;
        } else {
            // Accumulate Noise
            for (size_t k = 0; k < start_idx; ++k) {
                row0_ignored_sum += row0[k];
            }
            // Accumulate Valid Signal
            for (size_t k = start_idx; k < row0.size(); ++k) {
                row0_sum += row0[k];
            }
        }
        
        // Output: Formatted Truncation Report
        std::cout << "-----------------------------------------------------------" << std::endl;
        std::cout << "[Step 2] Low-Frequency Noise Truncation (Row 0)" << std::endl;
        // '4s' indicates 4 * Sigma
        std::cout << "  > Model Limit (" << std::noshowpos << kSignalRetentionZScore << "s): " 
                  << "Mu(" << std::fixed << std::setprecision(1) << m_base_mu << ") - "
                  << kSignalRetentionZScore << " * Sigma(" << m_base_sigma << ")" << std::endl;
        
        std::cout << "  > Cutoff Index:    " << std::setw(10) << start_idx << " (Values < this are Noise)" << std::endl;
        std::cout << "  > Noise (Removed): " << std::setw(10) << std::setprecision(0) << row0_ignored_sum << std::endl;
        std::cout << "  > Signal (Kept):   " << std::setw(10) << row0_sum << std::endl;
        std::cout << "-----------------------------------------------------------" << std::endl << std::endl;
    }

    // 2. Aggregate Global Statistics
    //    Total = Signal from Groups + Kept Signal from Row 0
    m_final_total_kmer_count = m_total_kmer_count_group + static_cast<double>(row0_sum);
    m_final_fitted_kmer_count = m_total_fitted_kmer_count_group;

    // 3. Compute Global 'Km' Score
    if (m_final_total_kmer_count == 0) {
        m_goodness_of_fit_metric = 0.0;
    } else {
        double ratio = m_final_fitted_kmer_count / m_final_total_kmer_count;
        m_goodness_of_fit_metric = (std::abs(ratio - 1.0) < 1e-9) ? 999999.0 : (1.0 / (1.0 - ratio));
    }

    // Sort results by row index for consistent reporting
    std::sort(m_per_row_results.begin(), m_per_row_results.end(), 
        [](const PerRowAnalysisResult& a, const PerRowAnalysisResult& b) {
            return a.row_index < b.row_index;
    });
}

double KmerDistributionAnalyzer::calculateOverlapSum(const std::vector<double>& model, const std::vector<uint64_t>& actual) {
    double sum = 0.0;
    size_t len = std::min(model.size(), actual.size());
    for (size_t j = 0; j < len; ++j) {
        // Overlap is the intersection of the Model area and Actual area
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
