/**
 * @file KmerDistributionAnalyzer.h
 * @brief Scientific K-mer Spectral Analysis Tool for Genome Assembly Quality Assessment.
 * @details This class implements a statistical approach to model K-mer frequency distributions.
 * It utilizes Robust Peak Detection, Adaptive Coordinate Descent (RSS optimization),
 * and Normal Scaling assumptions to calculate the Km consistency metric.
 * @author [Qilian Lin / Jintlich Research Group]
 * @version 1.0.0
 * @date 2025
 */

#ifndef KMER_DISTRIBUTION_ANALYZER_H
#define KMER_DISTRIBUTION_ANALYZER_H

#include <vector>
#include <cstdint> // For uint64_t
#include <cmath>
#include <string>

/**
 * @class KmerDistributionAnalyzer
 * @brief Core analyzer class handling the statistical modeling of K-mer stacks.
 */
class KmerDistributionAnalyzer {
public:
    /**
     * @struct PerRowAnalysisResult
     * @brief Container for analysis metrics for a specific K-mer frequency row.
     */
    struct PerRowAnalysisResult {
        size_t row_index;          ///< The index of the row (e.g., k-mer frequency)
        double row_mean;           ///< Fitted Gaussian Mean (Mu)
        double row_sigma;          ///< Derived Gaussian Sigma (Sigma)
        uint64_t row_kmer_count;   ///< Total observed K-mers in this row
        double row_fitted_count;   ///< Sum of the fitted distribution area (overlap)
        double row_fitted_pct;     ///< Percentage of data explained by the model
        double per_row_km;         ///< Calculated Km quality score for this specific row
    };

    /**
     * @brief Constructor using uint64_t for large genomic datasets.
     * @param kmer_counts 2D vector where [row][col] is the count of k-mers.
     */
    explicit KmerDistributionAnalyzer(const std::vector<std::vector<uint64_t>>& kmer_counts);

    /**
     * @brief Executes the full analysis pipeline: Initialize -> Fit Base -> Project -> Finalize.
     */
    void runAnalysis();

    // --- Getters ---
    double getGoodnessOfFitMetric() const { return m_goodness_of_fit_metric; }
    double getFinalTotalKmerCount() const { return m_final_total_kmer_count; }
    double getFinalFittedKmerCount() const { return m_final_fitted_kmer_count; }
    const std::vector<PerRowAnalysisResult>& getPerRowAnalysisResults() const { return m_per_row_results; }

private:
    // ========================================================================
    // Scientific Constants & Configuration
    // ========================================================================

    /**
     * @brief Conversion factor from Full Width at Half Maximum (FWHM) to Sigma.
     * Formula: Sigma = FWHM / (2 * sqrt(2 * ln(2))) ≈ 2.35482
     */
    static constexpr double kFWHM_To_Sigma_Factor = 2.35482004503; 

    /**
     * @brief Z-Score Cutoff for Signal Retention.
     * @details This constant defines the boundary between "True Signal" and "Noise/Outliers".
     * Used for:
     * 1. Defining the fitting window for RSS optimization.
     * 2. Truncating the low-frequency noise tail in Row 0.
     * * Value 4.0 corresponds to a ~99.9937% confidence interval in a normal distribution.
     * This conservative threshold ensures minimal loss of true genomic data while 
     * effectively removing sequencing errors.
     */
    static constexpr double kSignalRetentionZScore = 4.0;
    
    // --- Adaptive Optimization Hyperparameters ---
    static constexpr int    MAX_ITERATIONS = 150;      ///< Maximum descent steps
    static constexpr double CONVERGENCE_TOL = 1e-8;    ///< Minimum step size to continue
    static constexpr double INITIAL_STEP_MU = 1.0;     ///< Initial step size for Mean optimization
    static constexpr double INITIAL_STEP_SIGMA = 0.5;  ///< Initial step size for Sigma optimization
    static constexpr double STEP_DECAY = 0.5;          ///< Decay rate (learning rate reduction)

    // ========================================================================
    // Internal Pipelines
    // ========================================================================

    void initialize();            ///< Normalize data for numerical stability.
    void fitBaseParameters();     ///< Step 1: Determine Mu/Sigma of the main peak (Base Row).
    void projectModelToAllRows(); ///< Step 2: Apply Poisson-derived scaling to model other rows.
    void calculateFinalMetric();  ///< Step 3: Compute global statistics with noise truncation.

    // ========================================================================
    // Helper Methods
    // ========================================================================
    
    /**
     * @brief Robust Initial Parameter Estimation.
     * @details Replicates scipy.signal.find_peaks logic, selecting peaks based on 'Energy' (Height * Width).
     */
    static void estimateInitialParametersRobust(
        const std::vector<double>& data, 
        double& out_mean, 
        double& out_sigma
    );

    /**
     * @brief Calculates Residual Sum of Squares (RSS) between data and model.
     * @param left, right The index range (window) to consider for RSS calculation.
     */
    static double calculateRSS(
        const std::vector<double>& data,
        double mean,
        double sigma,
        size_t left,
        size_t right
    );

    static double normalPDF(double x, double mean, double sigma);
    
    double calculateOverlapSum(const std::vector<double>& model, const std::vector<uint64_t>& actual);

    // ========================================================================
    // Data Members
    // ========================================================================
    
    const std::vector<std::vector<uint64_t>>& m_kmer_counts;
    std::vector<std::vector<double>> m_normalized_distributions; // Normalized to Sum = 1.0
    std::vector<std::vector<double>> m_fitted_models;            // Scaled back to absolute counts

    // Base Parameters (The "Anchor" Row)
    double m_base_mu = 0.0;
    double m_base_sigma = 0.0;
    size_t m_base_row_index = 1;

    // Final Statistics
    double m_total_kmer_count_group = 0.0;
    double m_total_fitted_kmer_count_group = 0.0;
    double m_final_total_kmer_count = 0.0;
    double m_final_fitted_kmer_count = 0.0;
    double m_goodness_of_fit_metric = 0.0;

    std::vector<PerRowAnalysisResult> m_per_row_results;
};

#endif // KMER_DISTRIBUTION_ANALYZER_H
