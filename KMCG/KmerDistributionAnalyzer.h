#ifndef KMER_DISTRIBUTION_ANALYZER_H
#define KMER_DISTRIBUTION_ANALYZER_H

#include <vector>
#include <string>
#include <tuple> // Required for std::tuple in function signatures

/**
 * @class KmerDistributionAnalyzer
 * @brief Analyzes a series of k-mer frequency distributions by fitting Gaussian models.
 *
 * @details This class is designed to process a 2D dataset of k-mer counts, where each row represents
 * a distinct frequency distribution. The core methodology involves:
 * 1. Bootstrapping a set of initial model parameters (mean, sigma) from a single, high-quality distribution.
 * 2. Iteratively fitting Gaussian models to subsequent distributions using the bootstrapped parameters as a prior.
 * 3. Calculating an overall goodness-of-fit metric based on the cumulative overlap between the
 * observed data and the fitted models.
 */
class KmerDistributionAnalyzer {
public:
    /**
     * @brief Constructs the analyzer with the raw k-mer count data.
     * @param kmer_counts A constant reference to a 2D vector of integers. Each inner vector
     * represents the k-mer counts for a single distribution.
     */
    explicit KmerDistributionAnalyzer(const std::vector<std::vector<int>>& kmer_counts);

    /**
     * @brief Executes the full, multi-step analysis pipeline.
     * @details This is the main entry point for the class. It orchestrates the entire process from
     * data initialization and normalization to model fitting and final metric calculation.
     */
    void runAnalysis();

    // --- Result Getters ---

    /**
     * @brief Gets the final calculated goodness-of-fit metric.
     * @details The metric is calculated as 1.0 / (1.0 - R), where R is the ratio of the total
     * number of k-mers under the fitted curves to the total number of observed k-mers.
     * A higher value indicates a better fit. A value approaching infinity suggests a near-perfect fit.
     * @return The final goodness-of-fit metric, often referred to as 'Km'.
     */
    double getGoodnessOfFitMetric() const { return m_goodness_of_fit_metric; }

    /**
     * @brief Gets the list of overlap percentages for each analyzed distribution.
     * @details Each value in the vector represents the percentage of overlap between the observed
     * data and the fitted Gaussian model for the corresponding distribution.
     * @return A constant reference to a vector of doubles containing the overlap percentages.
     */
    const std::vector<double>& getOverlapPercentages() const { return m_overlap_percentages; }

private:
    // --- Configuration Constants ---
    // These constants control the behavior of the fitting algorithms.
    static constexpr double PEAK_WINDOW_THRESHOLD = 0.5;   ///< Percentage of the max value used to identify the primary "peak" for processing.
    static constexpr double INITIAL_MEAN_THRESHOLD = 0.5;  ///< Target cumulative probability for finding the initial mean window.
    static constexpr double SIGMA_GRID_SEARCH_MIN = 0.1;   ///< The minimum standard deviation to check in the initial grid search.
    static constexpr double SIGMA_GRID_SEARCH_MAX = 10.0;  ///< The maximum standard deviation to check in the initial grid search.
    static constexpr double SIGMA_GRID_SEARCH_STEP = 0.1;  ///< The step size for the initial sigma grid search.
    static constexpr double MEAN_OPTIMIZE_STEP = 0.01;     ///< The step size for the fine-grained mean optimization.
    static constexpr double SIGMA_OPTIMIZE_STEP = 0.001;   ///< The step size for the fine-grained sigma optimization.
    static constexpr double SCALING_FACTOR_MIN = 0.95;     ///< The minimum scaling factor to apply for maximizing overlap.
    static constexpr double SCALING_FACTOR_MAX = 1.0;      ///< The maximum scaling factor to apply for maximizing overlap.
    static constexpr double SCALING_FACTOR_STEP = 0.001;   ///< The step size for the scaling factor optimization.

    // --- Main Analysis Pipeline ---
    // These methods represent the sequential steps of the analysis.
    void initialize();
    void bootstrapModelParameters();
    void estimateSubsequentRowMeans();
    void averageRowMeans();
    void fitModelToSubsequentRows();
    void calculateFinalMetric();

    // --- Private Helper Methods ---

    /**
     * @brief Calculates the sum of the minimum values (overlap) between a model and actual data.
     * @param model_values A vector representing the fitted Gaussian model values.
     * @param actual_values A vector representing the original, observed integer counts.
     * @return The total sum of the overlap.
     */
    double calculateOverlapSum(const std::vector<double>& model_values, const std::vector<int>& actual_values);

    /**
     * @brief Calculates the Probability Density Function (PDF) of a normal distribution at a given point.
     * @param x The point at which to evaluate the PDF.
     * @param mean The mean (μ) of the distribution.
     * @param stddev The standard deviation (σ) of the distribution.
     * @return The value of the PDF at point x.
     */
    static double normalPDF(double x, double mean, double stddev);

    /**
     * @brief Calculates the Sum of Squared Errors (SSE) between the data and a Gaussian model within a specific range.
     * @param data The normalized data distribution.
     * @param mean The mean of the Gaussian model.
     * @param sigma The standard deviation of the Gaussian model.
     * @param left The inclusive starting index of the range.
     * @param right The inclusive ending index of the range.
     * @return The total SSE within the range.
     */
    static double calculateErrorInRange(
        const std::vector<double>& data,
        double mean,
        double sigma,
        size_t left,
        size_t right
    );

    /**
     * @brief Performs a coarse grid search to find an initial estimate for the standard deviation (sigma).
     * @details This function iterates through a range of sigma values, selecting the one that
     * minimizes the SSE as calculated by `calculateErrorInRange`.
     * @return A tuple containing {best_sigma, minimum_error}.
     */
    static std::tuple<double, double> findInitialSigma(
        const std::vector<double>& data,
        double initial_mean,
        size_t left,
        size_t right,
        double sigma_min,
        double sigma_max,
        double sigma_step
    );

    /**
     * @brief Performs a fine-grained grid search to refine the mean and sigma estimates.
     * @details This function searches in a narrow window around the initial estimates to find
     * a more precise pair of {mean, sigma} that minimizes the SSE.
     * @return A tuple containing {refined_mean, refined_sigma, minimum_error}.
     */
    static std::tuple<double, double, double> refineMeanAndSigma(
        const std::vector<double>& data,
        double initial_mean,
        double initial_sigma,
        size_t left,
        size_t right,
        double mean_step,
        double sigma_step
    );

    /**
     * @brief Performs a final optimization to maximize the direct percentage overlap, not just minimize SSE.
     * @details This step fine-tunes sigma and applies a scaling factor to the model's magnitude to
     * achieve the highest possible overlap with the original data.
     * @param original_vector The raw, unnormalized data counts.
     * @param gaussian_vector An output vector to be filled with the generated model values.
     * @param row_sum The total sum of k-mers in the original data row.
     * @return A tuple containing {final_sigma, final_scaling_factor, best_overlap_percentage}.
     */
    static std::tuple<double, double, double> finalizeParametersForMaxOverlap(
        const std::vector<double>& original_vector,
        std::vector<double>& gaussian_vector,
        double best_mean,
        double best_sigma,
        double row_sum,
        size_t left,
        size_t right
    );

    // --- Member Variables ---
    /** @struct RowFitResult
     * @brief Holds the intermediate fitting results for a single row of data.
     */
    struct RowFitResult {
        size_t row_index;             ///< The original index of the row.
        double estimated_mean;        ///< The mean estimated for this row's distribution.
        size_t left_bound;            ///< The left boundary of the window used for mean estimation.
        size_t right_bound;           ///< The right boundary of the window used for mean estimation.
        double total_kmer_count;      ///< The sum of all k-mers in this row.
    };

    // Input Data
    const std::vector<std::vector<int>>& m_kmer_counts; ///< A reference to the raw input k-mer count data.
    
    // Processed Data
    std::vector<std::vector<double>> m_normalized_distributions; ///< Normalized versions of the k-mer counts.
    std::vector<std::vector<double>> m_fitted_gaussian_models;   ///< The final generated Gaussian models for each row.

    // State Variables (carry information between analysis steps)
    double m_mean_search_radius = 0.0;    ///< The search radius for the mean in subsequent rows, derived from the bootstrap fit.
    double m_base_sigma = 0.0;            ///< The base standard deviation, determined from the bootstrap fit.
    double m_final_mean = 0.0;            ///< The final, averaged mean used for fitting all subsequent rows.
    std::vector<RowFitResult> m_intermediate_fit_results; ///< Stores the fitting results for each row from index 2 onwards.

    // Final Results
    double m_total_weighted_unfitted_sum = 0.0;
    double m_total_kmer_count = 0.0;        ///< The grand total of all observed k-mers across all distributions.
    double m_goodness_of_fit_metric = 0.0;  ///< The final calculated metric ('Km').
    std::vector<double> m_overlap_percentages; ///< Stores the final overlap percentage for each fitted row.
};

#endif // KMER_DISTRIBUTION_ANALYZER_H