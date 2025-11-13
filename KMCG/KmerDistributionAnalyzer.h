#ifndef KMER_DISTRIBUTION_ANALYZER_H
#define KMER_DISTRIBUTION_ANALYZER_H

#include <vector>
#include <string>
#include <tuple> // Required for std::tuple in function signatures

/**
 * @class KmerDistributionAnalyzer
 * @brief Analyzes a series of k-mer frequency distributions by fitting Gaussian models.
 *
 * @details This class is designed to process a 2D dataset of k-mer counts.
 * It uses the first 'n' (num_bootstrap_rows) distributions to establish baseline
 * parameters (mean and sigma) and then fits all subsequent rows (n+1 onward)
 * based on those parameters.
 *
 * The core methodology involves:
 * 1. Bootstrapping parameters by fitting the first 'num_bootstrap_rows' (e.g., n=3, rows 1, 2, 3).
 * 2. Finding the row 'k' (where k <= n) with the maximum k-mer count.
 * 3. Using the mean/radius from row 'k' as the starting point for sequential mean estimation
 * for rows n+1, n+2, ...
 * 4. Using the sigma from row 'k' to calculate all subsequent sigmas based on the
 * formula: sigma_i = (sigma_k * sqrt(k)) / sqrt(i).
 * 5. Calculating a single, global goodness-of-fit metric (Km).
 */
class KmerDistributionAnalyzer {
public:
    /**
     * @struct PerRowAnalysisResult
     * @brief Holds the detailed analysis breakdown for a single row.
     */
    struct PerRowAnalysisResult {
        size_t row_index;                 ///< The original index of the row (1, 2, 3...).
        double row_mean;                  ///< The estimated mean for this row (pre-averaging).
        double row_kmer_count;            ///< The total k-mers in this specific row.
        double row_fitted_kmer_count;     ///< The number of k-mers that were successfully fitted by the model.
        double row_fitted_percentage;     ///< The percentage of k-mers that were fitted (row_fitted_kmer_count / row_kmer_count).
        double per_row_km;                ///< The *PER-ROW* Km (1.0 / (1.0 - (row_fitted / row_total))).
    };

    /**
     * @brief Constructs the analyzer with the raw k-mer count data and bootstrap row count.
     * @param kmer_counts A constant reference to a 2D vector of integers.
     * @param num_bootstrap_rows The number of initial rows (1 to n) to use for
     * bootstrapping the model parameters.
     */
    explicit KmerDistributionAnalyzer(const std::vector<std::vector<int>>& kmer_counts, int num_bootstrap_rows = 1);

    /**
     * @brief Executes the full, multi-step analysis pipeline.
     */
    void runAnalysis();

    // --- Result Getters ---

    /**
     * @brief Gets the final calculated goodness-of-fit metric.
     * @return The final Km value.
     */
    double getGoodnessOfFitMetric() const { return m_goodness_of_fit_metric; }

    /**
     * @brief Gets the final total k-mer count (including row 0 penalty).
     * @return The total k-mer count used for the Km denominator.
     */
    double getFinalTotalKmerCount() const { return m_final_total_kmer_count; }

    /**
     * @brief Gets the final total *fitted* k-mer count.
     * @return The total fitted k-mer count used for the Km numerator.
     */
    double getFinalFittedKmerCount() const { return m_final_fitted_kmer_count; }

    /**
     * @brief Gets the list of overlap percentages for each analyzed distribution (rows 1 onward).
     * @return A constant reference to a vector of doubles containing the overlap percentages.
     */
    const std::vector<double>& getOverlapPercentages() const { return m_overlap_percentages; }

    /**
     * @brief Gets the detailed, row-by-row analysis breakdown.
     * @return A constant reference to a vector of PerRowAnalysisResult, sorted by row_index.
     */
    const std::vector<PerRowAnalysisResult>& getPerRowAnalysisResults() const { return m_per_row_results; }

private:
    // --- Configuration Constants ---
    static constexpr double PEAK_WINDOW_THRESHOLD = 0.5;
    static constexpr double INITIAL_MEAN_THRESHOLD = 0.5;
    static constexpr double SIGMA_GRID_SEARCH_MIN = 0.1;
    static constexpr double SIGMA_GRID_SEARCH_MAX = 10.0;
    static constexpr double SIGMA_GRID_SEARCH_STEP = 0.1;
    static constexpr double MEAN_OPTIMIZE_STEP = 0.01;
    static constexpr double SIGMA_OPTIMIZE_STEP = 0.001;
    static constexpr double SCALING_FACTOR_MIN = 0.95;
    static constexpr double SCALING_FACTOR_MAX = 1.0;
    static constexpr double SCALING_FACTOR_STEP = 0.001;


    // --- Main Analysis Pipeline ---
    void initialize();
    void bootstrapModelParameters();
    void estimateSubsequentRowMeans();
    void averageRowMeans();
    void fitModelToSubsequentRows();
    void calculateFinalMetric();

    // --- Private Helper Methods ---
    double calculateOverlapSum(const std::vector<double>& model_values, const std::vector<int>& actual_values);
    static double normalPDF(double x, double mean, double stddev);
    static double calculateErrorInRange(
        const std::vector<double>& data,
        double mean,
        double sigma,
        size_t left,
        size_t right
    );
    static std::tuple<double, double> findInitialSigma(
        const std::vector<double>& data,
        double initial_mean,
        size_t left,
        size_t right,
        double sigma_min,
        double sigma_max,
        double sigma_step
    );
    static std::tuple<double, double, double> refineMeanAndSigma(
        const std::vector<double>& data,
        double initial_mean,
        double initial_sigma,
        size_t left,
        size_t right,
        double mean_step,
        double sigma_step
    );
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
        size_t row_index;
        double estimated_mean;
        size_t left_bound;
        size_t right_bound;
        double total_kmer_count;
    };

    // Input Data
    const std::vector<std::vector<int>>& m_kmer_counts;
    const int m_num_bootstrap_rows; ///< The number of rows (1...n) to use for bootstrapping.

    // Processed Data
    std::vector<std::vector<double>> m_normalized_distributions;
    std::vector<std::vector<double>> m_fitted_gaussian_models;

    // State Variables
    double m_mean_search_radius = 0.0;    ///< Search radius from the max k-mer bootstrap row.
    double m_final_mean = 0.0;            ///< Mean from max k-mer row, later replaced by average of subsequent rows.
    
    // Unified Sigma parameters
    double m_base_sigma = 0.0;            ///< The sigma from the bootstrap row with the max k-mer count.
    size_t m_base_sigma_row_index = 1;    ///< The 1-based index (k) of the max k-mer bootstrap row.
    double m_base_sigma_kmer_count = -1.0; ///< The max k-mer count found during bootstrap (init to -1).

    // Intermediate results for rows (n+1) onward
    std::vector<RowFitResult> m_intermediate_fit_results;

    // Final Results
    double m_total_kmer_count = 0.0;
    
    // Per-group accumulators (now a single "group")
    double m_total_kmer_count_group = 0.0;   ///< Cumulative total k-mers (excluding row 0).
    double m_total_fitted_kmer_count_group = 0.0; ///< Cumulative *fitted* k-mers.
    
    // Final calculated values (including row 0)
    double m_final_total_kmer_count = 0.0;   ///< Final total k-mers for Km calc.
    double m_final_fitted_kmer_count = 0.0;  ///< Final fitted k-mers for Km calc.
    
    double m_goodness_of_fit_metric = 0.0;
    std::vector<double> m_overlap_percentages;

    // Detailed logging vector
    std::vector<PerRowAnalysisResult> m_per_row_results; ///< Stores the detailed breakdown for each row.
};

#endif // KMER_DISTRIBUTION_ANALYZER_H
