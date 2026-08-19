#include <iostream>
#include "kmc_file.h"
#include "nc_utils.h"
#include <queue>
#include <string> 
#include <fstream>
#include <cstdlib> 
#include <ctime> 
#include <time.h>
#include <climits> 
#include <cmath> 
#include <vector>
#include <array>
#include <filesystem>
#include <algorithm>
#include <regex>
#include <stdio.h>
#include <stdlib.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <dirent.h>
#include <cstring>
#include <iomanip>   
#include <sstream>   
#include <unistd.h>
#include <cstdio>
#include <chrono>
#include <sys/types.h>
#include <sys/stat.h>
#include <numeric>
#include <stdexcept>
#include <cassert>
#include <tuple>
#include <limits>
#include <random>
#include <zlib.h>  
#include <omp.h>
#include <thread> 
#include <condition_variable>
#include <iomanip> 
#include <memory>
#include <functional>
#include <cstdint>
#include <iomanip> 
#include <mutex>
#include <inttypes.h>
#include "./FM-index/FM-Index-master/FM.h"
#include "KmerDistributionAnalyzer.h"		
#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif
double normalPDF(double x, double mean, double stddev) {
    double coefficient = 1.0 / (stddev * sqrt(2 * M_PI));
    double exponent = exp(-0.5 * pow((x - mean) / stddev, 2));
    return coefficient * exponent;
}
double calculate_error_in_range(
    const std::vector<double>& data,
    double mean,
    double sigma,
    size_t left,
    size_t right
) {
    if (data.empty()) {
        throw std::invalid_argument("Data vector is empty.");
    }
    if (left >= data.size() || right >= data.size() || left > right) {
        throw std::invalid_argument("Invalid range: left and right must be within the bounds of the data vector.");
    }
    double sum_squared_error = 0.0;
    for (size_t i = left; i <= right; ++i) {
        double value = data[i];
        double pdf_value = normalPDF(i, mean, sigma);
        double error = value - pdf_value;
        sum_squared_error += error * error;
    }
    return sum_squared_error;
}
std::tuple<double, double> find_initial_sigma(
    const std::vector<double>& data,
    double initial_mean,
    size_t left,
    size_t right,
    double sigma_min = 0.1,
    double sigma_max = 10.0,
    double sigma_step = 0.1
) {
    double best_sigma = sigma_min;
    double min_error = std::numeric_limits<double>::max();
    for (double sigma = sigma_min; sigma <= sigma_max; sigma += sigma_step) {
        double error = calculate_error_in_range(data, initial_mean, sigma, left, right);
        if (error < min_error) {
            min_error = error;
            best_sigma = sigma;
        }
    }
    return std::make_tuple(best_sigma, min_error);
}
using namespace std;
void print_info(void);
double window_sum(const std::vector<double>& line, size_t start, size_t window_size) {
    return std::accumulate(line.begin() + start, line.begin() + start + window_size, 0.0);
}
double calculate_mse(const std::vector<double>& normalized_vector, const std::vector<double>& poisson_values, size_t left, size_t right) {
    double mse = 0.0;
    size_t count = 0;
    for (size_t i = left; i <= right; ++i) {
        mse += pow(normalized_vector[i] - poisson_values[i], 2);
        ++count;
    }
    return mse / count;
}
void normalize_data(const std::vector<unsigned long>& data_row, std::vector<double>& normalized_row) {
    long long row_sum = std::accumulate(data_row.begin(), data_row.end(), 0LL);
    if (row_sum != 0) {
        for (size_t i = 0; i < data_row.size(); ++i) {
            normalized_row[i] = static_cast<double>(data_row[i]) / row_sum;
        }
    } else {
        normalized_row = std::vector<double>(data_row.begin(), data_row.end());
        std::cout << "Warning: Row sum is zero. Normalization skipped." << std::endl;
    }
}
size_t find_initial_mean(const std::vector<double>& line) {
    double total_sum = std::accumulate(line.begin(), line.end(), 0.0);
    double target_sum = total_sum * 0.5;
    size_t initial_mean = 0;
    for (size_t window_size = 1; window_size <= line.size(); ++window_size) {
        bool found = false;
        for (size_t i = 0; i <= line.size() - window_size; ++i) {
            double current_window_sum = window_sum(line, i, window_size);
            if (current_window_sum >= target_sum) {
                auto max_iter = std::max_element(line.begin() + i, line.begin() + i + window_size);
                size_t max_index = std::distance(line.begin(), max_iter);
                initial_mean = max_index;
                found = true;
                break;
            }
        }
        if (found) {
            break;
        }
    }
    return initial_mean;
}
std::pair<size_t, size_t> find_process_window(const std::vector<double>& line, double percentage) {
    double max_value = *std::max_element(line.begin(), line.end());
    double threshold = max_value * percentage;
    size_t best_left = 0, best_right = 0;
    double best_window_max = 0.0;
    size_t left = 0;
    while (left < line.size()) {
        if (line[left] >= threshold) {
            size_t right = left;
            double current_window_max = line[left];
            while (right < line.size() && line[right] >= threshold) {
                current_window_max = std::max(current_window_max, line[right]);
                right++;
            }
            if (current_window_max > best_window_max ||
                (current_window_max == best_window_max && (right - left > best_right - best_left))) {
                best_left = left;
                best_right = right - 1;
                best_window_max = current_window_max;
            }
            left = right;
        } else {
            left++;
        }
    }
    return std::make_pair(best_left, best_right);
}
std::tuple<double, double> find_initial_sigma(
    const std::vector<double>& data,
    double initial_mean,
    double left,
    double right,
    double sigma_min = 0.1,
    double sigma_max = 10.0,
    double sigma_step = 0.1
) {
    if (data.empty()) {
        throw std::invalid_argument("Data vector is empty.");
    }
    if (sigma_min <= 0 || sigma_max <= 0 || sigma_min >= sigma_max) {
        throw std::invalid_argument("Invalid sigma range.");
    }
    if (sigma_step <= 0) {
        throw std::invalid_argument("Sigma step must be greater than 0.");
    }
    double best_sigma = sigma_min;
    double min_error = std::numeric_limits<double>::max();
    for (double sigma = sigma_min; sigma <= sigma_max; sigma += sigma_step) {
        try {
            double error = calculate_error_in_range(data, initial_mean, sigma, left, right);
            if (error < min_error) {
                min_error = error;
                best_sigma = sigma;
            }
        } catch (const std::exception& e) {
            std::cerr << "Error calculating for sigma = " << sigma << ": " << e.what() << std::endl;
        }
    }
    return std::make_tuple(best_sigma, min_error);
}
std::tuple<double, double, double> optimize_mean_and_sigma_grid_search(
    const std::vector<double>& data,
    double initial_mean,
    double initial_sigma,
    size_t left,
    size_t right,
    double mean_step = 0.01,
    double sigma_step = 0.001
) {
    if (data.empty()) {
        throw std::invalid_argument("Data vector is empty.");
    }
    if (left >= data.size() || right >= data.size() || left > right) {
        throw std::invalid_argument("Invalid range: left and right must be within the bounds of the data vector.");
    }
    double best_mean = initial_mean;
    double best_sigma = initial_sigma;
    double best_error = std::numeric_limits<double>::max();
    double mean_min = initial_mean - (initial_mean / 20);
    double mean_max = initial_mean + (initial_mean / 20);
    double sigma_min = initial_sigma - (initial_sigma / 2);
    double sigma_max = initial_sigma + (initial_sigma / 2);
    for (double mean = mean_min; mean <= mean_max; mean += mean_step) {
        for (double sigma = sigma_min; sigma <= sigma_max; sigma += sigma_step) {
            double error = calculate_error_in_range(data, mean, sigma, left, right);
            if (error < best_error) {
                best_mean = mean;
                best_sigma = sigma;
                best_error = error;
            }
        }
    }
    return std::make_tuple(best_mean, best_sigma, best_error);
}
double calculate_percentage(
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
    return (sum_smaller_values / sum_bigger_values) * 100;
}
std::tuple<double, double, double> optimize_sigma_and_row_sum(
    const std::vector<double>& original_vector,
    std::vector<double>& gaussian_vector,
    double best_mean,
    double best_sigma,
    double row_1_sum,
    size_t left,
    size_t right
) {
    double sigma_min = best_sigma * 0.9;
    double sigma_max = best_sigma;
    double sigma_step = 0.001;
    double A_min = 0.95;
    double A_max = 1.0;
    double A_step = 0.001;
    double final_sigma = best_sigma;
    double final_A = 1.0;
    double best_percentage = 0.0;
    for (double sigma = sigma_max; sigma >= sigma_min; sigma -= sigma_step) {
        for (double A = A_max; A >= A_min; A -= A_step) {
            double adjusted_row_1_sum = row_1_sum * A;
            for (size_t i = 0; i < gaussian_vector.size(); ++i) {
                gaussian_vector[i] = normalPDF(i, best_mean, sigma) * adjusted_row_1_sum;
            }
            double percentage = calculate_percentage(original_vector, gaussian_vector, left, right);
            if (percentage > best_percentage) {
                best_percentage = percentage;
                final_sigma = sigma;
                final_A = A;
            }
        }
    }
    return std::make_tuple(final_sigma, final_A, best_percentage);
}
bool help_or_version(int argc, char** argv)
{
	const std::string version = "--version";
	const std::string help = "--help";
	for (int i = 1; i < argc; ++i)
	{
		if (argv[i] == version || argv[i] == help)
			return true;
	}
	return false;
}
uint32 ROWS; 
uint32 COLS;  
uint32 WORDS_PER_CELL = 100;
uint32 MAX_WORD_LENGTH = 31;
uint32 kmernumber111 =0;
// uint32_t* result;
// std::ostringstream oss;
// uint32_t matches;
int flagchoose=0;
// int ASC = 0;
uint32_t cnt;
std::vector<std::string> sortedChr;
std::vector<long long> sortedChrnumber;
std::vector<std::string> sortedChr1;
std::vector<long long> sortedChrnumber1;
std::vector<std::string> sortedChr2;
std::vector<long long> sortedChrnumber2;
// std::ostringstream oss;
int32_t opt, samplerate;
double tt;
size_t kmerperbox=100;
void transformWord(std::string& word) 
{
	std::reverse(word.begin(), word.end());
	for (char& c : word) 
	{
		switch (c) {
			case 'A':
				c = 'T';
				break;
			case 'T':
				c = 'A';
				break;
			case 'C':
				c = 'G';
				break;
			case 'G':
				c = 'C';
				break;
			default:
				break;
		}
	}
}
bool appendWordToCell(std::vector<std::string>& cell, const std::string& word,uint64& kmernumofbox,std::mt19937& gen )
{
	size_t cellSize = cell.size();
    if (cellSize ==kmerperbox) 
	{
        
        std::uniform_int_distribution<> dis(0, kmernumofbox);
		uint64 random_num = dis(gen);
		if(random_num<kmerperbox)
		{
			cell[random_num] = word;
		}   
    }
	else if(cellSize<kmerperbox)
	{
		cell.push_back(word);
	}
	kmernumofbox++;
    return true;
}
bool appendWordToCell1(std::vector<std::string>& cell, const std::string& word )
{
	cell.push_back(word);
    return true;
}
void adjustCellSize(std::vector<std::string>& cell) {
    size_t cellSize = cell.size();
    if (cellSize > 100) {
        std::vector<std::string> newCell;
        size_t step = cellSize / 100;
        for (size_t i = 0; i < 100; ++i) {
            newCell.push_back(cell[i * step]);
        }
        cell = std::move(newCell);  
    }
}
bool appendWordToResult(std::vector<std::string>& cell, const std::string& word)
{
    cell.push_back(word); 
    return true;
}
bool appendkmertochr(std::vector<std::string>& cell, const std::string& word)
{
    cell.push_back(word); 
    return true;
}
bool belongtoFMIdx(FM* FMIdx,const std::string& word)
{
int cntnum1 = FMIdx->count(const_cast<uint8_t*>(reinterpret_cast<const uint8_t*>(word.c_str())), strlen(word.c_str()));
std::string str_word(word.c_str());
transformWord(str_word);
int cntnum2 = FMIdx->count(const_cast<uint8_t*>(reinterpret_cast<const uint8_t*>(str_word.c_str())), str_word.length());
if (cntnum1+cntnum2)
{
    return true;
}
else
{
	return false;
}
}
bool findStringInSelectedchrFMIdx(const std::vector<FM*>& selectedchrFMIdx, const std::string& word) {
	int sum=0;
    for (size_t i = 0; i < selectedchrFMIdx.size(); ++i) 
	{
		int cntnum1 = selectedchrFMIdx[i]->count(const_cast<uint8_t*>(reinterpret_cast<const uint8_t*>(word.c_str())), strlen(word.c_str()));
		std::string str_word(word.c_str());
		transformWord(str_word);
		int cntnum2 = selectedchrFMIdx[i]->count(const_cast<uint8_t*>(reinterpret_cast<const uint8_t*>(str_word.c_str())), str_word.length());
		sum+=cntnum1+cntnum2;
    }
    return sum; 
}
void mergeStrings(std::vector<std::string>& words) {
    if (words.empty()) return;
    size_t i = 0;
    while (i < words.size()) {
        size_t j = i + 1;
        while (j < words.size()) 
        {
            if (!words[j].empty() && words[i].substr(0, 31) == words[j].substr(0, 31)) {
                words[i] += words[j].substr(words[j].size() - 4);
                words[j] = "";
            }
            ++j;
        }
        ++i;
    }
    for (int i = words.size() - 1; i >= 0; i--) {
        if (words[i].empty()) {
            words.erase(words.begin() + i);
        }
    } 
}
std::string selectPartition() {
    FILE* pipe = popen("scontrol show partition | grep PartitionName", "r");
    if (!pipe) {
        std::cerr << "Unable to fetch partition names!" << std::endl;
        return "";
    }
    char buffer[256];
    std::vector<std::string> partitions;
    while (fgets(buffer, sizeof(buffer), pipe) != nullptr) {
        std::string line(buffer);
        size_t pos = line.find("PartitionName=");
        if (pos != std::string::npos) {
            std::string partition = line.substr(pos + 14);
            partition.erase(partition.find_last_not_of(" \n\r\t") + 1);
            partitions.push_back(partition); 
        }
    }
    pclose(pipe);
    for (const auto& partition : partitions) {
        if (partition.find("compute") != std::string::npos || 
            partition.find("cpu") != std::string::npos || 
            partition.find("CPU") != std::string::npos) {
            return partition;
        }
    }
    return !partitions.empty() ? partitions[0] : "";
}
std::string extractSequence(const std::string& filepath, const std::string& chrName, float start, float end) 
{
	std::ifstream fastaFile(filepath);
	std::string line;
	std::string sequence;
	bool found = false;
	while (getline(fastaFile, line)) 
	{
    if (line[0] == '>') {
        std::istringstream iss(line.substr(1));  
        std::string header_name;
        iss >> header_name;  // 自动截断到第一个空格或者制表符前
        if (header_name == chrName) {  
            std::cout << "Matched line: " << line << std::endl;
            found = true;
        } else {
            found = false;  
        }
    }
    if (found) {
        sequence += line;  // 收集序列内容（注意这里包含换行符）
    }
	}
	if (sequence.length() >= end*1e6) {
		return sequence.substr((start == 0) ? 0 : (start * 1e6 - 1), (end - start)*1e6);
	} else {
		std::cerr << "Error: Requested range (" << start << "-" << end << ") exceeds sequence length (" << sequence.length() << ")\n";
	}
	return "";
}
void deleteDirectory(const std::string& dirPath) {
    DIR* dir = opendir(dirPath.c_str());
    if (dir == nullptr) {
        std::cerr << "Failed to open directory: " << dirPath << std::endl;
        return;
    }
    struct dirent* entry;
    while ((entry = readdir(dir)) != nullptr) {
        // 跳过 "." 和 ".."
        if (entry->d_name == std::string(".") || entry->d_name == std::string("..")) {
            continue;
        }
        std::string filePath = dirPath + "/" + entry->d_name;
        struct stat statbuf;
        if (lstat(filePath.c_str(), &statbuf) == 0) {  // 使用 lstat 而不是 stat
            if (S_ISDIR(statbuf.st_mode)) {
                // 如果是子目录，递归删除
                deleteDirectory(filePath);
            } else {
                // 如果是文件或符号链接，删除文件
                if (unlink(filePath.c_str()) == 0) {  // 使用 unlink 删除文件和符号链接
                    std::cout << "Deleted file: " << filePath << std::endl;
                } else {
                    std::cerr << "Failed to delete file: " << filePath << std::endl;
                }
            }
        } else {
            std::cerr << "Failed to get file status: " << filePath << std::endl;
        }
    }
    closedir(dir);
    // 删除空目录
    if (rmdir(dirPath.c_str()) == 0) {
        std::cout << "Deleted directory: " << dirPath << std::endl;
    } else {
        std::cerr << "Failed to delete directory: " << dirPath << std::endl;
        perror("Error");  
    }
}
int findN(uint64  counter) {
    return std::ceil(std::log2(counter)); 
}
bool isInteger(double z) {
    return static_cast<long long>(z) == z;
}
// 在获取位置参数之后添加扩展名检查逻辑
auto check_fasta_extension = [](const std::string& filename) {
    const size_t dot_pos = filename.find_last_of('.');
    if (dot_pos == std::string::npos) return false;
    std::string ext = filename.substr(dot_pos + 1);
    std::transform(ext.begin(), ext.end(), ext.begin(), ::tolower);
    return (ext == "fa") || (ext == "fasta") || (ext == "fna");
};
// 使用智能指针管理互斥锁，避免复制问题
std::vector<std::vector<std::unique_ptr<std::mutex>>> mutexes;
// 初始化互斥锁矩阵
void initMutexes(size_t dim1, size_t dim2) {
    mutexes.resize(dim1);
    for (auto& row : mutexes) {
        row.resize(dim2);
        for (auto& mutex_ptr : row) {
            mutex_ptr = std::make_unique<std::mutex>();
        }
    }
}
void kmerlocate(const std::string& word,std::vector<FM*>& index,std::vector<std::vector<std::vector<std::string>>>& kmer2chr,uint32_t i,uint32_t j,const char** chrnamechr,std::vector<std::string>& sortedChr1,std::vector<std::string>& sortedChr2,double tt)
{
	uint32_t* result;
	uint32_t matches;
	int ASC = 0;
	std::ostringstream oss;
	int flagchoose=0;
	int t=0;
	for (auto* FMIdx : index) 
	{
		uint32_t cnt = FMIdx->count(const_cast<uint8_t*>(reinterpret_cast<const uint8_t*>(word.c_str())), strlen(word.c_str()));
		if (cnt>0)
		{
			result = FMIdx->locate(const_cast<uint8_t*>(reinterpret_cast<const uint8_t*>(word.c_str())), strlen(word.c_str()), &matches);
			std::string name(chrnamechr[t]);
			std::string target = name;
			auto it = std::find(sortedChr1.begin(), sortedChr1.end(), target);
			auto itt= std::find(sortedChr2.begin(), sortedChr2.end(), target);
			if (it != sortedChr1.end()) 
			{		
				ASC=std::distance(sortedChr1.begin(), it) ;
				flagchoose=1;
			} 
			if (itt != sortedChr2.end()) 
			{		
				ASC=std::distance(sortedChr2.begin(), itt) ;
				flagchoose=2;
			} 
			if(flagchoose==1) 
			{
			for(uint32_t z=0;z<matches;z++) 
			{	
				oss.str("");
				oss << std::setw(3) << std::setfill('0') << int(result[z]/(tt));	
                if (int(result[z]/(tt))>=1000)
                    {
                        std::cerr << "-t parameter configuration error.The specified value for the -t parameter is too low. Please provide a larger value."  << std::endl;
                    }
				appendWordToResult(kmer2chr[i][j],word.c_str()+std::string(1,char(ASC+33))+oss.str());	
			}
			}
			if(flagchoose==2) 
			{
			for(uint32_t z=0;z<matches;z++) 
			{	
				oss.str("");
				oss << std::setw(3) << std::setfill('0') <<ASC%100;
				appendWordToResult(kmer2chr[i][j],word.c_str()+std::string(1,char(sortedChr1.size()+33+(ASC/100)))+oss.str());
			}
			}		
			free(result);
			std::string str_word(word.c_str());
			transformWord(str_word);
			cnt = FMIdx->count(const_cast<uint8_t*>(reinterpret_cast<const uint8_t*>(str_word.c_str())), str_word.length());
			if (cnt>0)
			{
				result = FMIdx->locate(const_cast<uint8_t*>(reinterpret_cast<const uint8_t*>(str_word.c_str())), str_word.length(), &matches);
				std::string name(chrnamechr[t]);
				std::string target = name;
				auto it = std::find(sortedChr1.begin(), sortedChr1.end(), target);
				auto itt= std::find(sortedChr2.begin(), sortedChr2.end(), target);
				if (it != sortedChr1.end()) 
				{		
					ASC=std::distance(sortedChr1.begin(), it) ;
					flagchoose=1;
				} 
				if (itt != sortedChr2.end()) 
				{		
					ASC=std::distance(sortedChr2.begin(), itt) ;
					flagchoose=2;
				} 
				if(flagchoose==1) 
				{
				for(uint32_t z=0;z<matches;z++)
				{ 	
					oss.str("");
					oss << std::setw(3) << std::setfill('0') <<int(result[z]/(tt));
                    if (int(result[z]/(tt))>=1000)
                    {
                        std::cerr << "-t parameter configuration error.The specified value for the -t parameter is too low. Please provide a larger value."  << std::endl;
                    }
					appendWordToResult(kmer2chr[i][j],word.c_str()+std::string(1,char(ASC+33))+oss.str());
				}
				}
				if(flagchoose==2) 
				{
				for(uint32_t z=0;z<matches;z++)
				{
				oss.str("");
				oss << std::setw(3) << std::setfill('0') <<ASC%100;	
				appendWordToResult(kmer2chr[i][j],word.c_str()+std::string(1,char(sortedChr1.size()+33+(ASC/100)))+oss.str());
				}
				}		
				free(result);	
			}	
		}
		else
		{
			std::string str_word(word.c_str());
			transformWord(str_word);
			cnt = FMIdx->count(const_cast<uint8_t*>(reinterpret_cast<const uint8_t*>(str_word.c_str())), str_word.length());
			if (cnt>0)
			{
				result = FMIdx->locate(const_cast<uint8_t*>(reinterpret_cast<const uint8_t*>(str_word.c_str())), str_word.length(), &matches);
				std::string name(chrnamechr[t]);
				std::string target = name;
				auto it = std::find(sortedChr1.begin(), sortedChr1.end(), target);
				auto itt= std::find(sortedChr2.begin(), sortedChr2.end(), target);
				if (it != sortedChr1.end()) 
				{		
					ASC=std::distance(sortedChr1.begin(), it) ;
					flagchoose=1;
				} 
				if (itt != sortedChr2.end()) 
				{		
					ASC=std::distance(sortedChr2.begin(), itt) ;
					flagchoose=2;
				} 
				if(flagchoose==1) 
				{
				for(uint32_t z=0;z<matches;z++) 
				{	
					oss.str("");
					oss << std::setw(3) << std::setfill('0') <<int(result[z]/(tt));	
                    if (int(result[z]/(tt))>=1000)
                    {
                        std::cerr << "-t parameter configuration error.The specified value for the -t parameter is too low. Please provide a larger value."  << std::endl;
                    }
					appendWordToResult(kmer2chr[i][j],word.c_str()+std::string(1,char(ASC+33))+oss.str());
				}
				}
				if(flagchoose==2) 
				{
				for(uint32_t z=0;z<matches;z++) 
				{	
					oss.str("");
					oss << std::setw(3) << std::setfill('0') <<ASC%100;	
					appendWordToResult(kmer2chr[i][j],word.c_str()+std::string(1,char(sortedChr1.size()+33+(ASC/100)))+oss.str());//fprintf(out_file1,"%c%d ",char(sortedChr1.size()+33),ASC);
				}
				}		
				free(result);				
			}
		}
		t++;
	}
	
}
// 最快的并行化方案，同时优化文件写入
void optimizedParallelWithEfficientIO(const std::vector<std::vector<std::vector<std::string>>>& kmer2chr, 
                                     gzFile out_file, uint32_t ROWS, uint32_t COLS) {
    // 确定最佳线程数
    int num_threads = omp_get_max_threads();
    if (ROWS < static_cast<uint32_t>(num_threads * 2)) {
        num_threads = 1;
    }
    omp_set_num_threads(num_threads);
    // 计算每个线程处理的行范围
    uint32_t chunk_size = ROWS / num_threads;
    uint32_t remainder = ROWS % num_threads;
    // 预分配结果数组（使用unique_ptr避免复制开销）
    std::vector<std::unique_ptr<std::string>> results(ROWS);
    // 并行处理所有行
    #pragma omp parallel
    {
        int thread_id = omp_get_thread_num();
        uint32_t start_row = thread_id * chunk_size;
        uint32_t end_row = start_row + chunk_size;
        if (thread_id == num_threads - 1) {
            end_row += remainder; // 最后一个线程处理剩余的行
        }
        // 每个线程处理自己的行范围
        for (uint32_t i = start_row; i < end_row; ++i) {
            std::string row_result;
            // 预分配内存以提高性能
            row_result.reserve(COLS * 100); // 根据实际情况调整
            for (uint32_t j = 0; j < COLS; ++j) {
                std::vector<std::string> words = kmer2chr[i][j];
                mergeStrings(words);
                for (const auto& word : words) {
                    row_result.append(word);
                    row_result.append(" ");
                }
                row_result.append("\t");
            }
            row_result.append("\n");
            // 使用移动语义避免复制
            results[i] = std::make_unique<std::string>(std::move(row_result));
        }
    }
    // 批量写入文件以减少系统调用次数
    const size_t BATCH_SIZE = 1024 * 1024; // 1MB批次大小
    std::string buffer;
    buffer.reserve(BATCH_SIZE * 2); // 预分配2倍批次大小的缓冲区
    for (uint32_t i = 0; i < ROWS; ++i) {
        // 如果缓冲区即将满，先写入文件
        if (buffer.size() + results[i]->size() > BATCH_SIZE) {
            gzwrite(out_file, buffer.data(), buffer.size());
            buffer.clear();
        }
        // 将当前行添加到缓冲区
        buffer.append(*results[i]);
    }
    // 写入剩余的缓冲区内容
    if (!buffer.empty()) {
        gzwrite(out_file, buffer.data(), buffer.size());
    }
    // 写入最后的换行符
    const char* final_newline = "\n";
    gzwrite(out_file, final_newline, 1);
    // 确保所有数据都被刷新到磁盘
    gzflush(out_file, Z_FULL_FLUSH);
}
int main(int argc, char* argv[])
{	std::vector<time_t> timestamps;
	if (argc == 1 || help_or_version(argc, argv))
	{   
        printf("argc=%d",argc);
		print_info();
		return 0;
	}
	CKMCFile kmer_data_base;
	CKMCFile kmer_data_base1;
    CKMCFile kmer_data_base3;
    CKMCFile kmer_data_base4;
    CKMCFile kmer_data_base5;
	CKMCFile kmer_data_basefor3kmc;
	int32 i;
	uint32 min_count_to_set = 0;
	uint32 max_count_to_set = 0;
    uint32 xstep=10;
    uint32 ystep=1;
    uint32 p=1;
	uint32 s=1000;
	uint32 xbox=1002;
	uint32 ybox=302;
	bool gc=FALSE;
    bool location=TRUE;
    bool s_option_exists = false;
	int kmerlength=31;
	uint32 kmerlengthoffile1;
	uint32 kmerlengthoffile2;
	std::string CHR = "";
	std::string sex = "";
	std::string fragment = "";
	std::string input_file_name;
    std::string input_file_name1;
	std::string input_file_name2;
	std::string input_file_name3;
    std::string input_file_name4;
	std::string output_file_name;
	std::string output_file_name1;
    std::vector<std::vector<double>> fitted_models;
    // FILE * out_file;
	gzFile  out_file;
	// gzFile  out_file1;
    // gzFile  out_file2;
	// uint32 kmernumber111 =0;
	// uint32 ttt=0;    
	// uint32_t matches;
	// uint32_t* result;
	// int flagchoose=0;
	// uint32_t cnt;
	uint64 ki[102] = {0};  
	uint64 ki0[102] = {0};  
	uint64 sumforki=0;
	uint64 sumforki0=0;
	uint64 sumofcount=0;
	uint64 sumofgccount=0;
	uint64 sumofrow=0;
	uint64 maxsumofrow=0;
	uint64 maxnumofsumkmer=0;
    uint64 kmernumberofassembly=0;
    uint64 kmernumberofassembly1=0;
    uint64 kmernumberofassemblyof1=0;
    uint64 kmernumberofassemblyof2=0;
    uint64 maxkmernumberofassembly=0;
    uint64 testkmernumberofassembly=0;
    uint64 kmernumberofraw=0;
	double kii;
    double kom=0;
    double kmm=0;
    double kpp=0;
    // double ky0=0;
    double knor1=0;
    double knor2=0;
    // double kop=0;
	std::random_device rd;          
    std::mt19937 gen(rd());    // 引擎只需初始化一次
	//------------------------------------------------------------
	// Parse input parameters
	//------------------------------------------------------------
	if(argc < 3)
	{   
		print_info();
		return EXIT_FAILURE;
	}
std::vector<const char*> positional_args;
i = 1;
while (i < argc) {
    if (argv[i][0] == '-') {
        // 处理带参数的选项
        if (strcmp(argv[i], "-x") == 0) {
            if (i + 1 >= argc) {
                std::cerr << "Error: Missing value for -x option." << std::endl;
                exit(EXIT_FAILURE);
            }
            try {
                xstep = std::stoi(argv[i + 1]);
            } catch (...) {
                std::cerr << "Error: Invalid integer value for -x option." << std::endl;
                exit(EXIT_FAILURE);
            }
            i += 2;
        }
        else if (strcmp(argv[i], "-y") == 0) {
            if (i + 1 >= argc) {
                std::cerr << "Error: Missing value for -y option." << std::endl;
                exit(EXIT_FAILURE);
            }
            try {
                ystep = std::stoi(argv[i + 1]);
            } catch (...) {
                std::cerr << "Error: Invalid integer value for -y option." << std::endl;
                exit(EXIT_FAILURE);
            }
            i += 2;
        }
        else if (strcmp(argv[i], "-p") == 0) {
            if (i + 1 >= argc) {
                std::cerr << "Error: Missing value for -p option." << std::endl;
                exit(EXIT_FAILURE);
            }
            try {
                p = std::stoi(argv[i + 1]);
            } catch (...) {
                std::cerr << "Error: Invalid integer value for -p option." << std::endl;
                exit(EXIT_FAILURE);
            }
            i += 2;
        }
        else if (strcmp(argv[i], "-W") == 0) {
            if (i + 1 >= argc) {
                std::cerr << "Error: Missing value for -W option." << std::endl;
                exit(EXIT_FAILURE);
            }
            try {
                xbox = std::stoi(argv[i + 1]);
            } catch (...) {
                std::cerr << "Error: Invalid integer value for -W option." << std::endl;
                exit(EXIT_FAILURE);
            }
            i += 2;
        }
        else if (strcmp(argv[i], "-H") == 0) {
            if (i + 1 >= argc) {
                std::cerr << "Error: Missing value for -H option." << std::endl;
                exit(EXIT_FAILURE);
            }
            try {
                ybox = std::stoi(argv[i + 1]);
            } catch (...) {
                std::cerr << "Error: Invalid integer value for -H option." << std::endl;
                exit(EXIT_FAILURE);
            }
            i += 2;
        }
        else if (strcmp(argv[i], "-t") == 0) {
            if (i + 1 >= argc) {
                std::cerr << "Error: Missing value for -t option." << std::endl;
                exit(EXIT_FAILURE);
            }
            try {
                s = std::stoi(argv[i + 1]);
            } catch (...) {
                std::cerr << "Error: Invalid integer value for -t option." << std::endl;
                exit(EXIT_FAILURE);
            }
            i += 2;
        }
        else if (strcmp(argv[i], "-s") == 0) {
            if (i + 1 >= argc) {
                std::cerr << "Error: Missing value for -s option." << std::endl;
                exit(EXIT_FAILURE);
            }
            // CHR = argv[i + 1];
            input_file_name4 = argv[i + 1];
            s_option_exists = true;
            i += 2;
        }
        else if (strcmp(argv[i], "-o") == 0) {
            if (i + 1 >= argc) {
                std::cerr << "Error: Missing value for -o option." << std::endl;
                exit(EXIT_FAILURE);
            }
            output_file_name = argv[i + 1];
            i += 2;
        }
        else if (strcmp(argv[i], "-h") == 0) {
            if (i + 1 >= argc) {
                std::cerr << "Error: Missing value for -h option." << std::endl;
                exit(EXIT_FAILURE);
            }
            sex = argv[i + 1];
            i += 2;
        }
		else if (strcmp(argv[i], "-a") == 0) {
            if (i + 1 >= argc) {
                std::cerr << "Error: Missing value for -a option." << std::endl;
                exit(EXIT_FAILURE);
            }
            fragment = argv[i + 1];
            i += 2;
        }
        else if (strcmp(argv[i], "-c") == 0) {
            gc = true;
            i += 1;
        }
        else if (strcmp(argv[i], "-n") == 0) {
            location = FALSE;
            i += 1;
        }        
        else if (strcmp(argv[i], "-k") == 0) {
            if (i + 1 >= argc) {
                std::cerr << "Error: Missing value for -k option." << std::endl;
                exit(EXIT_FAILURE);
            }
            try {
                kmerlength = std::stoi(argv[i + 1]);
            } catch (...) {
                std::cerr << "Error: Invalid integer value for -k option." << std::endl;
                exit(EXIT_FAILURE);
            }
            i += 2;
        }
		else if (strcmp(argv[i], "-kpb") == 0) {
            if (i + 1 >= argc) {
                std::cerr << "Error: Missing value for -kpb option." << std::endl;
                exit(EXIT_FAILURE);
            }
            try {
                kmerperbox = std::stoi(argv[i + 1]);
            } catch (...) {
                std::cerr << "Error: Invalid integer value for -kpb option." << std::endl;
                exit(EXIT_FAILURE);
            }
            i += 2;
        }
        else {
            // 未知选项警告
            std::cerr << "Warning: Unknown option '" << argv[i] << "'" << std::endl;
            i += 1;
        }
    } else {
        positional_args.push_back(argv[i]);
        i += 1;
    }
}
// 检查位置参数数量
if (positional_args.size() != 3) {
    std::cerr << "Error: Required 3 positional arguments (input files). Got " 
              << positional_args.size() << "." << std::endl;
    print_info();
    return EXIT_FAILURE;
}
input_file_name  = positional_args[0];
input_file_name1 = positional_args[1];
input_file_name2 = positional_args[2];
//下面的代码功能是若输入文件为fa文件，则使用kmc将其变为kmc文件，先创建了一个临时文件夹然后将临时kmc文件放在里面，等程序运行完成后会自动销毁掉此文件夹。
// 初始化随机种子
std::srand(static_cast<unsigned int>(std::time(nullptr)));
// 生成10000到99999之间的随机数
int random_number = std::rand() % 90000 + 10000;
// 转换为字符串
std::string jobname = std::to_string(random_number);
std::string folderName = jobname+"tempfileofkmcg";
// 检查文件夹是否已经存在
struct stat info;
if (stat(folderName.c_str(), &info) != 0) {
	// 文件夹不存在，尝试创建文件夹
	if (mkdir(folderName.c_str(), 0777) == 0) {//注意，linux和macOS使用mkdir
		std::cout << "Folder '" << folderName << "' created successfully.\n";
	} else {
		std::cout << "Failed to create folder '" << folderName << "'.\n";
	}
} else if (info.st_mode & S_IFDIR) {
	std::cout << "Folder '" << folderName << "' already exists.\n";
} else {
	std::cout << "'" << folderName << "' exists but is not a directory.\n";
}
const bool valid_ext = check_fasta_extension(input_file_name);
if (valid_ext) 
{
    std::cout << 1 << std::endl;  // 符合条件时输出1
// 生成临时目录名：folderName + 文件名（不含路径和扩展名）
size_t lastSlashPos = input_file_name.find_last_of("/\\");  // 查找最后一个路径分隔符
std::string fileName = (lastSlashPos != std::string::npos) ? 
                        input_file_name.substr(lastSlashPos + 1) : 
                        input_file_name;
size_t dotPos = fileName.rfind('.');  // 查找扩展名的点号
std::string baseName = (dotPos != std::string::npos) ? 
                        fileName.substr(0, dotPos) : 
                        fileName;
std::string tempDirNameofinput_file_name = folderName + "/" +baseName;  // 拼接结果
// 构建命令字符串
std::ostringstream commandStream;
commandStream << "kmc -k" << kmerlength 
              << " -t10 -m190 -ci1 -cs16700000 -fm -v "
              << "\"" << input_file_name << "\" "
              << "\"" << tempDirNameofinput_file_name << "\" ./ | awk '{print $4}'";
	std::string command = commandStream.str();
				// 执行命令
			int ret = system(command.c_str());
			// 检查执行结果
			if (ret == 0) {
				// 成功逻辑
				std::cout << "'" << tempDirNameofinput_file_name << "' 创建成功.\n";
			} else {
				std::cerr << "Unable to call kmc."<< std::endl;// 失败逻辑
			}
input_file_name=tempDirNameofinput_file_name;
} 
const bool valid_ext1 = check_fasta_extension(input_file_name1);
if (valid_ext1) 
{
    std::cout << 1 << std::endl;  // 符合条件时输出1
	// 生成临时目录名
// 生成临时目录名：folderName + 文件名（不含路径和扩展名）
size_t lastSlashPos = input_file_name1.find_last_of("/\\");  // 查找最后一个路径分隔符
std::string fileName = (lastSlashPos != std::string::npos) ? 
                        input_file_name1.substr(lastSlashPos + 1) : 
                        input_file_name1;
size_t dotPos = fileName.rfind('.');  // 查找扩展名的点号
std::string baseName = (dotPos != std::string::npos) ? 
                        fileName.substr(0, dotPos) : 
                        fileName;
std::string tempDirNameofinput_file_name1 = folderName +"/" + baseName;  // 拼接结果
// 构建命令字符串
std::ostringstream commandStream;
commandStream << "kmc -k" << kmerlength 
              << " -t10 -m190 -ci1 -cs65535 -fm -v "
              << "\"" << input_file_name1 << "\" "
              << "\"" << tempDirNameofinput_file_name1 << "\" ./ | awk '{print $4}'";
	std::string command = commandStream.str();
				// 执行命令
			int ret = system(command.c_str());
			// 检查执行结果
			if (ret == 0) {
				// 成功逻辑
				std::cout << "'" << tempDirNameofinput_file_name1 << "' 创建成功.\n";
			} else {
				std::cerr << "Unable to call kmc."<< std::endl;// 失败逻辑
			}
input_file_name1=tempDirNameofinput_file_name1;
} 
	samplerate = DEFAULT_SAMPLERATE;
	double tt=s*1000.0;
	const int sizeofx = xbox;
	const int sizeofy = ybox;
	ROWS=sizeofy;
	COLS=sizeofx;
	std::vector<std::string> array[sizeofy][sizeofx];
	std::vector<std::vector<uint64>> gccount(sizeofy, std::vector<uint64>(sizeofx));
	std::vector<std::vector<uint64>> gccount1(sizeofy, std::vector<uint64>(sizeofx));
	std::vector<std::vector<std::vector<std::string>>> kmer2chr(sizeofy, std::vector<std::vector<std::string>>(sizeofx));
	vector<vector<uint64>> count(sizeofy, vector<uint64>(sizeofx));
	vector<vector<uint64>> count1(sizeofy, vector<uint64>(sizeofx));
    vector<vector<uint64>> count2(sizeofy, vector<uint64>(sizeofx));
	vector<vector<uint64>> countof32(sizeofy, vector<uint64>(32));
	vector<vector<uint64>> countnumof32(sizeofy, vector<uint64>(32));
	std::vector<double> coefmatrixofcount0(32);
	std::vector<double> coefmatrixofcount(32);
    vector<vector<uint64>> countstep1(521, vector<uint64>(130));
	vector<vector<uint64>> countstep2(521, vector<uint64>(130));
    vector<vector<uint64>> countstep3(521, vector<uint64>(130));
	std::vector<std::vector<uint64>> kmernumofbox(ybox, std::vector<uint64>(xbox, 0));
    std::vector<std::vector<double>> mm(521, std::vector<double>(130));
	std::string tempFileName ="";
	std::string tempDirName = "";
	bool tempDirCreated = false;
	if(tempDirCreated){}
	std::string filepath1 = input_file_name2;//fasta
	// std::string filePath = input_file_name3;//fa.fai
	std::string filePath = input_file_name2 + ".fai";
    std::ifstream file(filePath);
    std::string line;
    std::vector<std::string> chr;
    std::vector<long long> chrnumber;
	std::string item;
	// std::vector<std::string> scrsequence(SelectedchrArray.size());//循环生成三文件的kmc
	std::vector<std::vector<std::vector<int>>> kmc3count(  
		1,                                            
		std::vector<std::vector<int>>(                      
			sizeofy,                                        
			std::vector<int>(sizeofx)                       
		)
	);
    if (file.is_open()) {//下面的代码将从fai文件提取染色体及其长度信息，将相关信息保存
        while (std::getline(file, line)) {
            std::istringstream iss(line);
            std::string token;
            int count = 0;
            std::string firstStr;
            std::string secondStr;
            while (std::getline(iss, token, '\t')) {
                if (count == 0) {
                    firstStr = token;
                } else if (count == 1) {
                    secondStr = token;
                    break;
                }
                count++;
            }
            long long number = std::stoll(secondStr);
            chr.push_back(firstStr);
            chrnumber.push_back(number);
        }
        file.close();
    } else {
		std::cout << "faifile:" << filePath << "\n";
        std::cerr << "Unable to open fai file.Please check if the fai file exists or is damaged.The fai file should be placed in the same directory as the FASTA file." << std::endl;
        return 1;
    }
    struct Entry {
        size_t index;
        long long number;
    };
    std::vector<Entry> indexedNumbers;
    for (size_t i = 0; i < chrnumber.size(); ++i) {
        indexedNumbers.push_back({i, chrnumber[i]});
    }
    std::sort(indexedNumbers.begin(), indexedNumbers.end(), [](const Entry& a, const Entry& b) {
        return a.number > b.number;
    });
    std::vector<std::string> sortedChr;
    std::vector<long long> sortedChrnumber;
    std::vector<std::string> sortedChr1;
    std::vector<long long> sortedChrnumber1;
    std::vector<std::string> sortedChr2;
    std::vector<long long> sortedChrnumber2;
    for (const auto& entry : indexedNumbers) {
        sortedChr.push_back(chr[entry.index]);
        sortedChrnumber.push_back(entry.number);
    }
    for (size_t i = 0; i < sortedChr.size(); ++i) {
        if (sortedChrnumber[i]<1000000)//当染色体的长度小于1M时，判定为手脚架，将其名字存入字符串数组
        {
            sortedChr2.push_back(sortedChr[i]);
            sortedChrnumber2.push_back(sortedChrnumber[i]);
        }
        else
        {
            sortedChr1.push_back(sortedChr[i]);
            sortedChrnumber1.push_back(sortedChrnumber[i]);
        }
    }
	std::ifstream file1(filepath1);
	if (!file1.is_open()) {
		std::cerr << "Unable to open fa file.Please check if the fa file exists or is damaged." << filepath1 << std::endl;
		return 1;
	}
	std::vector<std::string> sequences;
	std::vector<std::string> sequenceNames;
	std::string line1;
	std::string currentSequence;
	bool collecting = false;
	while (std::getline(file1, line1)) {//从fa文件里收集每条染色体的序列以及染色体的名字，以便之后建立索引。
		if (!line1.empty() && line1[0] == '>') {
			if (collecting) {
				if (!currentSequence.empty()) {
					sequences.push_back(currentSequence);
					currentSequence.clear();
				}
			}
			collecting = true;
			size_t firstWhitespace = line1.find_first_of(" \t");
			if (firstWhitespace != std::string::npos) {
				sequenceNames.push_back(line1.substr(1, firstWhitespace - 1));
			} else {
				sequenceNames.push_back(line1.substr(1));
			}
		} else if (collecting) {
			currentSequence += line1;
		}
	}
	if (collecting && !currentSequence.empty()) {
		sequences.push_back(currentSequence);
	}
	file1.close();
	const char** chrchr = new const char*[sequences.size()];
	for (size_t i = 0; i < sequences.size(); ++i) {//将收集的染色体的序列存入字符串数组
		chrchr[i] = sequences[i].c_str();
	}
	const char** chrnamechr = new const char*[sequenceNames.size()];
	for (size_t i = 0; i < sequenceNames.size(); ++i) {//将fa文件的染色体的名字存入字符串数组
		chrnamechr[i] = sequenceNames[i].c_str();
	}	
	std::cout << "检查点0" << " 抵达成功.\n";
time_t now0 = std::time(nullptr);
std::cout << "当前时间0: " << std::ctime(&now0);
	int32_t  samplerate;
	const int num_strings = sequences.size();
	std::vector<FM*> FMIdx(num_strings);
	samplerate = DEFAULT_SAMPLERATE;
	// FM::verbose = 0;
	#pragma omp parallel for 
	for (int t = 0; t < num_strings; ++t) //为了保存多个索引，建立了索引数组。
	{
		// std::cout << "检查点0.5" << " 抵达成功.\n";
		uint32_t n = strlen(chrchr[t]);
		uint8_t* T = (uint8_t*) malloc((n + 1) * sizeof(uint8_t));
		if (!T) {
			perror("error allocating memory");
			exit(EXIT_FAILURE);
		}
		memcpy(T, chrchr[t], n);
		T[n] = '\0';
		FMIdx[t] = new FM(T, n, samplerate);
		if (!FMIdx[t]) 
		{
			perror("error building index");
			free(T); 
			exit(EXIT_FAILURE);
		}	
	}
	std::cout << "检查点1" << " 抵达成功.\n";
 now0 = std::time(nullptr);
std::cout << "当前时间1: " << std::ctime(&now0);
//这一段代码是为了生成gc纠正矩阵的存储文件
	if((out_file = gzopen (output_file_name.c_str(),"wb")) == NULL)
	{   
		std::cerr << "Failed to open output gz file."  << std::endl;
		print_info();
		return EXIT_FAILURE;
	}
	std::filesystem::path path_obj(output_file_name);
	std::string new_filename = "fix_" + path_obj.filename().string();
	std::filesystem::path new_path = path_obj.parent_path() / new_filename;
	output_file_name1 = new_path.string();
	if (output_file_name1.empty()) 
	{
    std::cerr << "Output filename1 is empty!" << std::endl;
    return EXIT_FAILURE;
	}
	gzprintf(out_file,"KMCG1\t%u\t%u\t",ybox,xbox);
	gzprintf(out_file,"%d\t%d\t\n",kmerlength,int(tt));
	for (size_t i = 0; i < sortedChr1.size(); ++i) {//输出染色体的名称
		gzprintf(out_file,"%s\t",sortedChr1[i].c_str());
	}
	for (size_t i = 0; i < sortedChr2.size(); ++i) {//输出脚手架的名称，每一百个脚手架为一个单位
		gzprintf(out_file,"%s",sortedChr2[i].c_str());
		if ((i+1)%100!=0){
			if(i!=sortedChr2.size()-1)
			{
			gzprintf(out_file,":");
			}
		}
		if ((i+1)%100==0)
		{
		gzprintf(out_file,"\t");
		}
	}	
	gzprintf(out_file,"\n");
	for (size_t i = 0; i < sortedChr1.size(); ++i) 
	{
		if((int)ceil((double)sortedChrnumber1[i] / tt)>999)//如果某个染色体的长度超过999个单位长度，则将单元长度改为原来的10倍。
		{
			tt=tt*10;
		}
	}
	for (size_t i = 0; i < sortedChr1.size(); ++i) {
		gzprintf(out_file, "%d\t", (int)ceil((double)sortedChrnumber1[i] / tt));//输出各个染色体有多少个单元长度
	}
	for (size_t i = 0; i < (sortedChr2.size()/100); ++i) {
		gzprintf(out_file,"100\t");//输出脚手架组有多少个单元长度，默认每个脚手架组长度为100个单元长度
	}
	if(sortedChr2.size()%100!=0)
	{
		gzprintf(out_file,"%zu",(sortedChr2.size()%100));//输出最后一个脚手架组的长度，其小于或等于100个单位长度。
	}
	gzprintf(out_file,"\n");
		// setvbuf(out_file, NULL ,_IOFBF, 1 << 24);
            //以下是性染色体相关的代码
			if(CHR.size()==0&&sex.size()!=0)
			{
			std::vector<std::string> sexchrArray;
			std::stringstream sexchr(sex); 
			std::string item;
			while (getline(sexchr, item, ',')) {
				sexchrArray.push_back(item);
			}
			std::ifstream file1_1(filepath1);
			if (!file1_1.is_open()) {
				std::cerr << "Unable to open fa file.Please check if the fa file exists or is damaged." << std::endl;
				return 1;
			}
			std::vector<std::string> sequences;
			std::string line;
			std::string currentSequence;
			std::string chrname;
			bool collecting = false;
			while (std::getline(file1_1, line)) {
				if (!line.empty() && line[0] == '>') {
					size_t firstWhitespace = line.find_first_of(" \t");
					if (firstWhitespace != std::string::npos) {
						chrname = line.substr(1, firstWhitespace - 1);
					} else {
						chrname = line.substr(1);
					}
					size_t foundchr = chrname.find("chr");
					if ((foundchr== std::string::npos)) {
						collecting = false;
					} else {
						collecting = true;
					}
					if (collecting) {
						currentSequence += line +"\n";
					}
				} else if (collecting) {
					currentSequence += line + "\n";
				}
			}
			file1_1.clear();  
			file1_1.seekg(0); 
			while (std::getline(file1_1, line)) {
				if (!line.empty() && line[0] == '>') {
					size_t firstWhitespace = line.find_first_of(" \t");
					if (firstWhitespace != std::string::npos) {
						chrname = line.substr(1, firstWhitespace - 1);
					} else {
						chrname = line.substr(1);
					}
					size_t foundchr = chrname.find("chr");
					if (std::find(sexchrArray.begin(), sexchrArray.end(), chrname) != sexchrArray.end()||(foundchr== std::string::npos)) {
						collecting = false;
					} else {
						collecting = true;
					}
					if (collecting) {
						currentSequence += line + "_"+"\n";
					}
				} else if (collecting) {
					currentSequence += line + "\n";
				}
			}
			std::stringstream cs(currentSequence);
			std::vector<std::string> oddLines;
			int lineNumber1 = 1;  
			while (std::getline(cs, line)) {
				if (lineNumber1 % 2 != 0) {
					oddLines.push_back(line);
				}
				lineNumber1++;
			}
			file1_1.close();  
			std::string tempFileName = "kmcgtemp" + jobname + ".fa";
			std::string tempDirName = "kmcgtemp" + jobname;
			std::ofstream outFile(tempFileName);
			if (!outFile.is_open()) {
				std::cerr << "Unable to create file: " <<tempFileName<< std::endl;
				return 1;
			}
			outFile << currentSequence;
			outFile.close();
			std::ifstream filetemp(tempFileName);
			if (!filetemp.is_open()) {
				std::cerr << "Unable to open kmcgtemp: " <<tempFileName<< std::endl;
				return 1;
			}
            std::string partitionName = selectPartition();
            if (partitionName.empty()) {
                std::cerr << "No valid partition found!" << std::endl;
                return 1;
            }
			std::string command = std::string("kmc -k31 -t10 -m190 -ci1 -cs65535 -fm -v ")
				+
				"\"" + tempFileName + "\" " +   
				"\"" + tempDirName + "\" " +    
				"./ | awk '{print $4}'";        

			// 执行命令
			int ret = system(command.c_str());
			// 检查执行结果
			if (ret == 0) {
				// 成功逻辑
			} else {
				std::cerr << "Unable to call kmc."<< std::endl;// 失败逻辑
			}

			input_file_name1 = tempDirName;
            tempDirCreated = true;
			}
			std::cout << "检查点2" << " 抵达成功.\n";
			 now0 = std::time(nullptr);
std::cout << "当前时间2: " << std::ctime(&now0);
			if (!kmer_data_base.OpenForRA(input_file_name1))
			{   
				printf("OpenForRA0 failed");
				print_info();
				return EXIT_FAILURE ;
			}
			else
			{
			if (!kmer_data_base1.OpenForListing(input_file_name))
			{   
				printf("openforlisting0 failed");
				print_info();
				return EXIT_FAILURE ;
			}
			else
			{
				uint32 _kmer_length;
				uint32 _mode;
				uint32 _counter_size;
				uint32 _lut_prefix_length;
				uint32 _signature_len;
				uint32 _min_count;
				uint64 _max_count;
				uint64 _total_kmers;
				kmer_data_base1.Info(_kmer_length, _mode, _counter_size, _lut_prefix_length, _signature_len, _min_count, _max_count, _total_kmers);
				CKmerAPI kmer_object(_kmer_length);	
				std::cout << "_kmer_length: " << _kmer_length << std::endl;		
				kmerlengthoffile1=_kmer_length;	
				if(min_count_to_set)
				if (!(kmer_data_base1.SetMinCount(min_count_to_set)))
						return EXIT_FAILURE;
				if(max_count_to_set)
				if (!(kmer_data_base1.SetMaxCount(max_count_to_set)))
						return EXIT_FAILURE;	
				size_t readtime=0;
				CKmerAPI kmerstr;
				uint64 counter;
				std::queue<uint32_t> num;
				uint64 counter1;
				int y=0;
				int sizeofY =xbox;
					while (kmer_data_base1.ReadNextKmer(kmer_object, counter))
					{
                    if(counter>3000000){std::cout << kmer_object.to_string() << std::endl;}
                    kmernumberofraw+=counter;
					if (readtime%p==0)
					{
						bool is_kmer_found = kmer_data_base.CheckKmer(kmer_object, counter1);
						if (is_kmer_found) 
						{
						  //什么都不做
						}			
						else {
							if (counter <=xstep*(xbox-2) )
							{	           
								y=ceil((counter)/(xstep*1.0));  
							}
							else
							{
								y=sizeofY-1;
							}
							count[0][y]++;
							// kmernumofbox[0][y]++;
                            if(location){appendWordToCell(array[0][y],kmer_object.to_string().c_str(),kmernumofbox[0][y],gen);}
							countstep1[0][counter > 128 ? 129 : counter]++;	
							int count_gc = 0;
							std::string kmer_str = kmer_object.to_string();  // 提前保存字符串
							for (char ch : kmer_str) {                      // 遍历已保存的字符串
								if (ch == 'G' || ch == 'g' || ch == 'C' || ch == 'c') {
									count_gc++;
								}
							}
							countof32[0][count_gc]+=counter;
							countnumof32[0][count_gc]++;
							gccount[0][y]+=count_gc;
							if(counter>100)
							{
								ki0[101]+=counter;
							}
							else
							{
								ki0[counter]+=counter;
							}
						}
					}
					readtime++;
					}	
			}
			}	
						std::cout << "检查点3" << " 抵达成功.\n";
						 now0 = std::time(nullptr);
std::cout << "当前时间3: " << std::ctime(&now0);
			kmer_data_base.Close();
			kmer_data_base1.Close();
			if (!kmer_data_base3.OpenForRA(input_file_name))
			{   
				printf("OpenForRA failed");
				print_info();
				return EXIT_FAILURE ;
			}
			else
			{
			if (!kmer_data_base4.OpenForListing(input_file_name1))
			{   
				printf("openforlisting failed");
				print_info();
				return EXIT_FAILURE ;
			}
			else
			{
				uint32 _kmer_length;
				uint32 _mode;
				uint32 _counter_size;
				uint32 _lut_prefix_length;
				uint32 _signature_len;
				uint32 _min_count;
				uint64 _max_count;
				uint64 _total_kmers;
				kmer_data_base4.Info(_kmer_length, _mode, _counter_size, _lut_prefix_length, _signature_len, _min_count, _max_count, _total_kmers);
				CKmerAPI kmer_object(_kmer_length);
				std::cout << "_kmer_length1: " << _kmer_length << std::endl;		
				kmerlengthoffile2=_kmer_length;	
				if(kmerlengthoffile2!=kmerlengthoffile1)
				{
					std::cout << "kmerlengthoffile2!=kmerlengthoffile1" << std::endl;
					return EXIT_FAILURE;
				}
				if(min_count_to_set)
				if (!(kmer_data_base4.SetMinCount(min_count_to_set)))
						return EXIT_FAILURE;
				if(max_count_to_set)
				if (!(kmer_data_base4.SetMaxCount(max_count_to_set)))
						return EXIT_FAILURE;	
				size_t readtime=0;
				CKmerAPI kmerstr;
				uint64 counter;
				std::queue<uint32_t> num; 
				uint64 counter1;
				int x=0,y=0;
				const int sizeofx = xbox;
				const int sizeofy = ybox;
					while (kmer_data_base4.ReadNextKmer(kmer_object, counter))
					{
                    if(counter>maxkmernumberofassembly)
                    {
                        maxkmernumberofassembly=counter;
                        std::cout << "maxkmernumberofassembly="<<maxkmernumberofassembly<< std::endl;

                    }
                    kmernumberofassembly+=counter;
                    kmernumberofassembly1++;
                    if(counter==1){kmernumberofassemblyof1++;}
                    if(counter==2){kmernumberofassemblyof2++;}
					if (readtime%p==0)
					{
						bool is_kmer_found = kmer_data_base3.CheckKmer(kmer_object, counter1);
                        testkmernumberofassembly+=counter1;
					// if((counter==1&&59<counter1&&counter1<91))
						if (is_kmer_found) 
						{
							if (counter1 <=xstep*(xbox-2))
							{				   			
								x=ceil(counter1/(xstep*1.0));			
							}
							else 
							{
								x=sizeofx-1;
							}
							if (counter <=ystep*(ybox-2) )
							{							
								y=ceil(counter/(ystep*1.0));
							}
							else 
							{
								y=sizeofy-1;    
							}					
							count[y][x]++;   
                            if(location){appendWordToCell(array[y][x],kmer_object.to_string().c_str(),kmernumofbox[y][x],gen);}
							countstep1[counter < 513 ? counter : 512+findN(counter)-9][isInteger(counter1/(counter*1.0))? ((counter1/counter)<129? (counter1/counter):129): (floor(counter1/counter*1.0)<129? floor(counter1/counter*1.0):129)]++;
							int count_gc = 0;
							std::string kmer_str = kmer_object.to_string();  // 提前保存字符串
							for (char ch : kmer_str) {                      // 遍历已保存的字符串
								if (ch == 'G' || ch == 'g' || ch == 'C' || ch == 'c') {
									count_gc++;
								}
							}
							countof32[y][count_gc]+=counter1;
							countnumof32[y][count_gc]++;
							gccount[y][x]+=count_gc;
							if(counter1>100)
							{
								ki[101]+=counter1;
							}
							else
							{
								ki[counter1]+=counter1;
							}
						}			
						else {
							if (counter <=ystep*(ybox-2) )
								{	                                                                   
									y=ceil(counter/(ystep*1.0));
								}
							else {
								y=sizeofy-1;
								}
							count[y][0]++;
                            if(location){appendWordToCell(array[y][0],kmer_object.to_string().c_str(),kmernumofbox[y][0],gen);}
							countstep1[counter < 513 ? counter : 512+findN(counter)-9][0]++;
							int count_gc = 0;
							std::string kmer_str = kmer_object.to_string();  // 提前保存字符串
							for (char ch : kmer_str) {                      // 遍历已保存的字符串
								if (ch == 'G' || ch == 'g' || ch == 'C' || ch == 'c') {
									count_gc++;
								}
							}
							countof32[y][count_gc]+=0;
							countnumof32[y][count_gc]++;
							gccount[y][0]+=count_gc;
						}
					
					}
					readtime++;
					}
			}
			}
			kmer_data_base3.Close();
			kmer_data_base4.Close();
			for (int i = 0; i < sizeofy; ++i) //输出KMCG矩阵，同时记录信息
				{	
					for (int j = 0; j <sizeofx ; ++j) {
						sumofrow+=count[i][j];
						gzprintf(out_file, "%ld\t", count[i][j]); 
					}
					gzprintf(out_file, "\n");
					if(sumofrow>maxsumofrow&&(i!=sizeofy-1))
					{
						maxsumofrow=sumofrow;
						maxnumofsumkmer=i;
					}
					sumofrow=0;
				}
				gzprintf(out_file, "\n");
			std::cout << "检查点4" << " 抵达成功.\n";
			 now0 = std::time(nullptr);
std::cout << "当前时间4: " << std::ctime(&now0);
            //这一段是性染色体相关代码
            if(CHR.size()==0&&sex.size()!=0)
            {
                if (std::filesystem::exists(tempFileName)) {
                    if (std::remove(tempFileName.c_str()) == 0) {
                        std::cout << "Temporary file deleted: " << tempFileName << std::endl;
                    } else {
                        std::cerr << "Failed to delete temporary file: " << tempFileName << std::endl;
                    }
                } else {
                    std::cerr << "Temporary file not found: " << tempFileName << std::endl;
                }

                if (std::filesystem::exists(tempDirName)) {
                    try {
                        std::filesystem::remove_all(tempDirName);
                        std::cout << "Temporary directory deleted: " << tempDirName << std::endl;
                    } catch (const std::filesystem::filesystem_error& e) {
                        std::cerr << "Failed to delete temporary directory: " << tempDirName << " - " << e.what() << std::endl;
                    }
                } else {
                    std::cerr << "Temporary directory not found: " << tempDirName << std::endl;
                }

            }
#pragma omp parallel for collapse(2) schedule(dynamic)//多线程溯源kmer的来源
for (uint32_t i = 0; i < (sizeof(array) / sizeof(array[0])); ++i) {
    for (uint32_t j = 0; j < (sizeof(array[0]) / sizeof(array[0][0])); ++j) {
        for (const auto& word : array[i][j]) {
            kmerlocate(word, FMIdx, kmer2chr, i, j, chrnamechr, sortedChr1, sortedChr2, tt);
        }
    }
}
				std::cout << "检查点5" << " 抵达成功.\n";
			 now0 = std::time(nullptr);
std::cout << "当前时间5: " << std::ctime(&now0);
    optimizedParallelWithEfficientIO(kmer2chr, out_file, ROWS, COLS);//输出朔源location矩阵
				std::cout << "检查点6" << " 抵达成功.\n";
			 now0 = std::time(nullptr);
std::cout << "当前时间6: " << std::ctime(&now0);

	/*
	KmerDistributionAnalyzer to get percentage list and Km
	*/   
	double best_mean = 0.0;
	double final_sigma = 0.0;
	bool row1_found = false;
	try {
		// 1. Create the analyzer with the data
		KmerDistributionAnalyzer analyzer(countstep1);
		// 2. Run the entire analysis process
		analyzer.runAnalysis();

        // 1. 获取原始分析结果 (只读)
        const auto& source_models = analyzer.getFittedModels();

        // 3. 调整大小并拷贝数据
        fitted_models.resize(source_models.size());
        for (size_t i = 0; i < source_models.size(); ++i) {
            fitted_models[i].resize(source_models[i].size());
            for (size_t k = 0; k < source_models[i].size(); ++k) {
                fitted_models[i][k] = source_models[i][k];
            }
        }


		// 3. Output Percentage List & Km
		const auto& results = analyzer.getPerRowAnalysisResults();
		for (const auto& res : results) {
				// gzprintf(out_file, "%.2f\t", res.row_fitted_pct);
				if (res.row_index == 1) {
						best_mean = res.row_mean;
						final_sigma = res.row_sigma;
						row1_found = true;
				}
		}
        for (size_t i = 0; i < countstep1.size(); ++i) //输出压缩矩阵
                {
                    for (size_t j = 0; j < countstep1[i].size(); ++j) 
                    {
                        if(i==0&&(j<best_mean-4*final_sigma))
                        {
                            gzprintf(out_file, "0\t"); 
                        }
                        else
                        {
                            gzprintf(out_file, "%ld\t", countstep1[i][j]);
                        }
                    }
                    gzprintf(out_file, "\n");
                }
        for (const auto& res : results) {
                gzprintf(out_file, "%.2f\t", res.row_fitted_pct);
        }
			// gzprintf(out_file, "\nKm= %.6f\t", analyzer.getGoodnessOfFitMetric()); 
			if(row1_found) {
						// 这里可以根据需要调整，或者不输出，只用于内部计算
						gzprintf(out_file, "\nμ:%.4f\tσ:%.4f\t", best_mean, final_sigma);
			}
            gzprintf(out_file, "Km:%.6f\t", analyzer.getGoodnessOfFitMetric()); 
	} catch (const std::exception& e) {
			std::cerr << "Error during Kmer Analysis: " << e.what() << std::endl;
			gzprintf(out_file, "\tError_in_Analysis\tKm= 0.000000\t");
	}
	for(size_t i=std::max(static_cast<int>(std::floor(best_mean-4*final_sigma)), 11);i<102;i++)
	{
			sumforki0+=ki0[i];
	}
	for(size_t i=std::max(static_cast<int>(std::floor(best_mean-4*final_sigma)), 11);i<102;i++)
	{
			sumforki+=ki[i];
	}
	if (sumforki0 != 0 && (sumforki0 + sumforki) != 0)
	{
		kii=1/(1-((sumforki*1.0)/(sumforki0+sumforki)));
		gzprintf(out_file, "Ki:%.6f\t", kii);
	}
	else if(sumforki0 == 0)
	{
	sumforki0=1;
	kii=1/(1-((sumforki*1.0)/(sumforki0+sumforki)));
	gzprintf(out_file, "Ki:%.6f\t", kii);	
	}
	else
	{
		std::cerr << "Error:  All data are equal to zero." << std::endl;
	}
                                                                                                //    以下代码是纠正GC含量图的代码
// 使用引用避免重复访问同一行数据
const auto& target_row = countof32[maxnumofsumkmer];
auto max_it = std::max_element(target_row.begin(), target_row.end());
uint64_t max_value = *max_it;

//预计算避免重复访问
const auto& count_row = countnumof32[maxnumofsumkmer];

// std::cout << "系数计算结果:\n";
for (size_t i = 0; i < 32; ++i) {
    // 避免整数除法，直接使用浮点数除法
    double ratio = static_cast<double>(max_value) / count_row[i];
    coefmatrixofcount[i] = ratio;
    

}

			if (!kmer_data_base.OpenForRA(input_file_name1))
			{   
				printf("OpenForRA0 failed");
				print_info();
				return EXIT_FAILURE ;
			}
			else
			{
			if (!kmer_data_base1.OpenForListing(input_file_name))
			{   
				printf("openforlisting0 failed");
				print_info();
				return EXIT_FAILURE ;
			}
			else
			{
				uint32 _kmer_length;
				uint32 _mode;
				uint32 _counter_size;
				uint32 _lut_prefix_length;
				uint32 _signature_len;
				uint32 _min_count;
				uint64 _max_count;
				uint64 _total_kmers;
				kmer_data_base1.Info(_kmer_length, _mode, _counter_size, _lut_prefix_length, _signature_len, _min_count, _max_count, _total_kmers);
				CKmerAPI kmer_object(_kmer_length);				
				if(min_count_to_set)
				if (!(kmer_data_base1.SetMinCount(min_count_to_set)))
						return EXIT_FAILURE;
				if(max_count_to_set)
				if (!(kmer_data_base1.SetMaxCount(max_count_to_set)))
						return EXIT_FAILURE;	
				size_t readtime=0;
				CKmerAPI kmerstr;
				uint64 counter;
				std::queue<uint32_t> num;
				uint64 counter1;
				int y=0;
				int sizeofY =xbox;
					while (kmer_data_base1.ReadNextKmer(kmer_object, counter))
					{
					if (readtime%p==0)
					{
						bool is_kmer_found = kmer_data_base.CheckKmer(kmer_object, counter1);					
						if (is_kmer_found) 
						{					
						}			
						else {
							int count_gc = 0;
							std::string kmer_str = kmer_object.to_string();  // 提前保存字符串
							for (char ch : kmer_str) {                      // 遍历已保存的字符串
								if (ch == 'G' || ch == 'g' || ch == 'C' || ch == 'c') {
									count_gc++;
								}
							}
                            if(counter>best_mean-4*final_sigma)
                            {
                                kom+=counter/best_mean;
                            }
							counter=counter*coefmatrixofcount[count_gc];
							if (counter <=xstep*(xbox-2) )
							{	           
								y=ceil((counter)/(xstep*1.0));  
							}
							else
							{
								y=sizeofY-1;
							}
							count1[0][y]++;

							countstep2[0][counter > 128 ? 129 : counter]++;
							gccount1[0][y]+=count_gc;
						}
					}
					readtime++;
					}				
			}
			}	
						std::cout << "检查点3" << " 抵达成功.\n";
						 now0 = std::time(nullptr);
std::cout << "当前时间3: " << std::ctime(&now0);
			// kmer_data_base.Close();
			kmer_data_base1.Close();
			if (!kmer_data_base3.OpenForRA(input_file_name))
			{   
				printf("OpenForRA failed");
				print_info();
				return EXIT_FAILURE ;
			}
			else
			{
			if (!kmer_data_base4.OpenForListing(input_file_name1))
			{   
				printf("openforlisting failed");
				print_info();
				return EXIT_FAILURE ;
			}
			else
			{
				uint32 _kmer_length;
				uint32 _mode;
				uint32 _counter_size;
				uint32 _lut_prefix_length;
				uint32 _signature_len;
				uint32 _min_count;
				uint64 _max_count;
				uint64 _total_kmers;
				kmer_data_base4.Info(_kmer_length, _mode, _counter_size, _lut_prefix_length, _signature_len, _min_count, _max_count, _total_kmers);
				CKmerAPI kmer_object(_kmer_length);
				if(min_count_to_set)
				if (!(kmer_data_base4.SetMinCount(min_count_to_set)))
						return EXIT_FAILURE;
				if(max_count_to_set)
				if (!(kmer_data_base4.SetMaxCount(max_count_to_set)))
						return EXIT_FAILURE;	
				size_t readtime=0;
				CKmerAPI kmerstr;
				uint64 counter;
				std::queue<uint32_t> num;
				uint64 counter1;
				int x=0,y=0;
				const int sizeofx = xbox;
				const int sizeofy = ybox;			
					while (kmer_data_base4.ReadNextKmer(kmer_object, counter))
					{
					if (readtime%p==0)
					{
						bool is_kmer_found = kmer_data_base3.CheckKmer(kmer_object, counter1);
					// if((counter==1&&59<counter1&&counter1<91))					
						if (is_kmer_found) 
						{   
                            // std::cout << "k0-的y>0时x："<<static_cast<uint64_t>(counter1) <<std::endl;    
							int count_gc = 0;
							std::string kmer_str = kmer_object.to_string();  // 提前保存字符串
							for (char ch : kmer_str) {                      // 遍历已保存的字符串
								if (ch == 'G' || ch == 'g' || ch == 'C' || ch == 'c') {
									count_gc++;
								}
							}


                            if(counter!=0)
                            {              
                                // std::cout << "k0-的y>0时y："<<static_cast<uint64_t>(counter) <<"x:"<<static_cast<uint64_t>(counter1) <<std::endl;       
                                if ((counter1/counter)>128)
                                {
                                    kom+=(counter1/best_mean)-counter;
                                }
                            } 
                            else
                            {
                                // std::cout << "k0-的y==0时y："<<static_cast<uint64_t>(counter) <<"x:"<<static_cast<uint64_t>(counter1) <<std::endl;   
                                // std::cout << "k0-的y==0,miu-4sigma:"<<best_mean-4*final_sigma<<std::endl;  
                                if(counter1>best_mean-4*final_sigma)
                                    {
                                        kom+=counter1/best_mean;
                                    }
                            }
							counter1=counter1*coefmatrixofcount[count_gc];
							if (counter1 <=xstep*(xbox-2))
							{				   			
								x=ceil(counter1/(xstep*1.0));			
							}
							else 
							{
								x=sizeofx-1;
							}
							if (counter <=ystep*(ybox-2) )
							{							
								y=ceil(counter/(ystep*1.0));
							}
							else 
							{
								y=sizeofy-1;    
							}					
							count1[y][x]++;           
							countstep2[counter < 513 ? counter : 512+findN(counter)-9][isInteger(counter1/(counter*1.0))? ((counter1/counter)<129? (counter1/counter):129): (floor(counter1/counter*1.0)<129? floor(counter1/counter*1.0):129)]++;
							gccount1[y][x]+=count_gc;
						}			
						else {
							int count_gc = 0;
							std::string kmer_str = kmer_object.to_string();  // 提前保存字符串
							for (char ch : kmer_str) {                      // 遍历已保存的字符串
								if (ch == 'G' || ch == 'g' || ch == 'C' || ch == 'c') {
									count_gc++;
								}
							}
							if (counter <=ystep*(ybox-2) )
								{	                                                                   
									y=ceil(counter/(ystep*1.0));
								}
							else {
								y=sizeofy-1;
								}
							count1[y][0]++;
							countstep2[counter < 513 ? counter : 512+findN(counter)-9][0]++;
							gccount1[y][0]+=count_gc;
						}					
					}
					readtime++;
					}
			}
			}
			kmer_data_base3.Close();
			kmer_data_base4.Close();

        for (size_t i = 0; i < countstep1.size(); ++i) //输出压缩矩阵
                {
                    for (size_t j = 0; j < countstep1[i].size(); ++j) 
                    {
                        mm[i][j] = countstep1[i][j] - fitted_models[i][j];
                        // if(i==0)
                        // {
                        //     kmm+=countstep1[i][j]*((j/best_mean));
                        //     ky0+=countstep1[i][j]*((j/best_mean));
                        // }
                        if(0<i&&i<513)
                        {
                            if(j<size_t(best_mean))
                            {
                                kpp+=max(0.0,(countstep1[i][j]-fitted_models[i][j]))*(1-(j/best_mean))*i;
                            }
                            else if(size_t(best_mean)<j&&j<129)
                            {
                                kmm+=max(0.0,(countstep1[i][j]-fitted_models[i][j]))*((j/best_mean)-1)*i;
                                knor1+=max(0.0,(countstep1[i][j]-fitted_models[i][j]))*((j/best_mean)-1)*i;
                            }
                        }
                        else if(512<i&&i<519)
                        {
                            if(j<size_t(best_mean))
                            {
                                kpp+=max(0.0,(countstep1[i][j]-fitted_models[i][j]))*(1-(j/best_mean))* std::pow(2.0,(i-512)+8.3);
                            }
                            else if(size_t(best_mean)<j&&j<129)
                            {
                                kmm+=max(0.0,(countstep1[i][j]-fitted_models[i][j]))*((j/best_mean)-1)* std::pow(2.0,(i-512)+8.3);
                                knor2+=max(0.0,(countstep1[i][j]-fitted_models[i][j]))*((j/best_mean)-1)* std::pow(2.0,(i-512)+8.3);
                            }
                        }  
                    }
                }
            kmm+=kom;
            gzprintf(out_file, "k-:%.3f\t", (kmm/(kmernumberofassembly+kmm-kpp))*100);
            gzprintf(out_file, "k+:%.3f\t", (kpp/kmernumberofassembly)*100);
            std::cout <<"k0-:"<<kom << "\n";
            std::cout <<"knor1-:"<<knor1 << "\n";
            std::cout <<"knor2-:"<<knor2 << "\n";
            std::cout <<"kmernumberofassembly="<<kmernumberofassembly << "\n";
            gzprintf(out_file,"kmernumberofassembly:");
            gzprintf(out_file, "%" PRIu64 "\n", kmernumberofassembly);

                                    std::cout << "检查点33" << " 抵达成功.\n";
                         now0 = std::time(nullptr);
std::cout << "当前时间3: " << std::ctime(&now0);
        if(gc)
        {
            for (int i = 0; i < sizeofy; ++i) 
                {
                    for (int j = 0; j <sizeofx ; ++j) {
                        sumofgccount+=gccount[i][j];
                    }
                }
                gzprintf(out_file, "GCpercentage\t%\t");
                gzprintf(out_file, "%f\n", sumofgccount/(sumofcount*31.0)); 
                for (int i = 0; i < sizeofy; ++i) //输出gc含量
                    {
                        for (int j = 0; j <sizeofx ; ++j) 
                        {   
                            if(count[i][j]!=0)
                            {
                                if(gccount[i][j]==0)
                                {
                                    gzprintf(out_file, "001\t");
                                }
                                else if(gccount[i][j]==count[i][j] * kmerlength)
                                {
                                gzprintf(out_file, "999\t");
                                }
                                else
                                {
                                int gcresult = round((static_cast<double>(gccount[i][j]) / (count[i][j] * kmerlength)) * 1000.0);
                                gzprintf(out_file, "%d\t", gcresult); 
                                }
                            }
                            else
                            {
                            gzprintf(out_file, "000\t");
                            }
                        }
                        gzprintf(out_file, "\n");
                    }
                    gzprintf(out_file, "\n");
        }
                                std::cout << "检查点333" << " 抵达成功.\n";
                         now0 = std::time(nullptr);
std::cout << "当前时间3: " << std::ctime(&now0);

if (s_option_exists)
{
            if (!kmer_data_base5.OpenForRA(input_file_name4))//这一行复制过来的本来是kmer_data_base而不是kmer_data_base5，这里kmer_data_base变成kmer_data_base5是为了符合3文件的逻辑
            {   
                printf("OpenForRA0 failed");
                print_info();
                return EXIT_FAILURE ;
            }
            else
            {
            if (!kmer_data_base1.OpenForListing(input_file_name))
            {   
                printf("openforlisting0 failed");
                print_info();
                return EXIT_FAILURE ;
            }
            else
            {
                uint32 _kmer_length;
                uint32 _mode;
                uint32 _counter_size;
                uint32 _lut_prefix_length;
                uint32 _signature_len;
                uint32 _min_count;
                uint64 _max_count;
                uint64 _total_kmers;
                kmer_data_base1.Info(_kmer_length, _mode, _counter_size, _lut_prefix_length, _signature_len, _min_count, _max_count, _total_kmers);
                CKmerAPI kmer_object(_kmer_length); 
                std::cout << "_kmer_length: " << _kmer_length << std::endl;     
                kmerlengthoffile1=_kmer_length; 
                if(min_count_to_set)
                if (!(kmer_data_base1.SetMinCount(min_count_to_set)))
                        return EXIT_FAILURE;
                if(max_count_to_set)
                if (!(kmer_data_base1.SetMaxCount(max_count_to_set)))
                        return EXIT_FAILURE;    
                size_t readtime=0;
                CKmerAPI kmerstr;
                uint64 counter;
                std::queue<uint32_t> num;
                uint64 counter1;
                uint64 counter5;
                int y=0;
                int sizeofY =xbox;
                    while (kmer_data_base1.ReadNextKmer(kmer_object, counter))
                    {
                    if (readtime%p==0)
                    {   bool is_kmer_found1=kmer_data_base5.CheckKmer(kmer_object, counter5);
                        if(!is_kmer_found1){continue;}
                        bool is_kmer_found = kmer_data_base.CheckKmer(kmer_object, counter1);
                        
                        if (is_kmer_found) 
                        {
                        }           
                        else {
                            if (counter <=xstep*(xbox-2) )
                            {              
                                y=ceil((counter)/(xstep*1.0));  
                            }
                            else
                            {
                                y=sizeofY-1;
                            }
                            count2[0][y]++;
                            countstep3[0][counter > 128 ? 129 : counter]++; 

                        }
                    }
                    readtime++;
                    }
                    
            }
            }   
                        std::cout << "检查点3" << " 抵达成功.\n";
                         now0 = std::time(nullptr);
std::cout << "当前时间3: " << std::ctime(&now0);
            // kmer_data_base.Close();                                      //kmer_data_base是完整的组装数据
            kmer_data_base1.Close();
            kmer_data_base5.Close();
            if (!kmer_data_base3.OpenForRA(input_file_name))
            {   
                printf("OpenForRA failed");
                print_info();
                return EXIT_FAILURE ;
            }
            else
            {
            if (!kmer_data_base5.OpenForListing(input_file_name4))//这里复制过来本来是kmer_data_base4而不是kmer_data_base5，这里改成5是为了3文件的逻辑
            {   
                printf("openforlisting failed");
                print_info();
                return EXIT_FAILURE ;
            }
            else
            {
                uint32 _kmer_length;
                uint32 _mode;
                uint32 _counter_size;
                uint32 _lut_prefix_length;
                uint32 _signature_len;
                uint32 _min_count;
                uint64 _max_count;
                uint64 _total_kmers;
                kmer_data_base4.Info(_kmer_length, _mode, _counter_size, _lut_prefix_length, _signature_len, _min_count, _max_count, _total_kmers);
                CKmerAPI kmer_object(_kmer_length);
                std::cout << "_kmer_length1: " << _kmer_length << std::endl;        
                kmerlengthoffile2=_kmer_length; 
                if(kmerlengthoffile2!=kmerlengthoffile1)
                {
                    std::cout << "kmerlengthoffile2!=kmerlengthoffile1" << std::endl;
                    return EXIT_FAILURE;
                }
                if(min_count_to_set)
                if (!(kmer_data_base4.SetMinCount(min_count_to_set)))
                        return EXIT_FAILURE;
                if(max_count_to_set)
                if (!(kmer_data_base4.SetMaxCount(max_count_to_set)))
                        return EXIT_FAILURE;    
                size_t readtime=0;
                CKmerAPI kmerstr;
                uint64 counter;
                std::queue<uint32_t> num; 
                uint64 counter1;
                uint64 counter5;
                int x=0,y=0;
                const int sizeofx = xbox;
                const int sizeofy = ybox;
                    while (kmer_data_base5.ReadNextKmer(kmer_object, counter5))
                    {
                    if (readtime%p==0)
                    {   bool is_kmer_found1=kmer_data_base.CheckKmer(kmer_object, counter);
                        if(is_kmer_found1){}
                        bool is_kmer_found = kmer_data_base3.CheckKmer(kmer_object, counter1);
                    // if((counter==1&&59<counter1&&counter1<91))
                    
                        if (is_kmer_found) 
                        {
                            if (counter1 <=xstep*(xbox-2))
                            {                           
                                x=ceil(counter1/(xstep*1.0));           
                            }
                            else 
                            {
                                x=sizeofx-1;
                            }
                            if (counter <=ystep*(ybox-2) )
                            {                           
                                y=ceil(counter/(ystep*1.0));
                            }
                            else 
                            {
                                y=sizeofy-1;    
                            }                   
                            count2[y][x]++;   
                            countstep3[counter < 513 ? counter : 512+findN(counter)-9][isInteger(counter1/(counter*1.0))? ((counter1/counter)<129? (counter1/counter):129): (floor(counter1/counter*1.0)<129? floor(counter1/counter*1.0):129)]++;

                        }           
                        else {
                            if (counter <=ystep*(ybox-2) )
                                {                                                                      
                                    y=ceil(counter/(ystep*1.0));
                                }
                            else {
                                y=sizeofy-1;
                                }
                            count2[y][0]++;
                            // if(location){appendWordToCell(array[y][0],kmer_object.to_string().c_str(),kmernumofbox[y][0],gen);}
                            
                            countstep3[counter < 513 ? counter : 512+findN(counter)-9][0]++;

                        }
                    
                    }
                    readtime++;
                    }   
            }
            }
            kmer_data_base3.Close();
            kmer_data_base4.Close();
            kmer_data_base5.Close();
            std::cout << "检查点666" << " 抵达成功.\n";
             now0 = std::time(nullptr);
std::cout << "当前时间2: " << std::ctime(&now0);
// 找到最后一个 '/' 的位置
size_t last_slash_pos = input_file_name4.find_last_of("/");
// 提取最后一个 '/' 之后的部分（即文件名）
std::string filename_only = input_file_name4.substr(last_slash_pos + 1);
// 输出文件名
gzprintf(out_file, "%s\t#\t", filename_only.c_str());
            // gzprintf(out_file, "文件名\t#\n");
            std::cout << "检查点6666" << " 抵达成功.\n";
             now0 = std::time(nullptr);
std::cout << "当前时间2: " << std::ctime(&now0);

    try {
        // 1. Create the analyzer with the data
        KmerDistributionAnalyzer analyzer(countstep3);
        // 2. Run the entire analysis process
        analyzer.runAnalysis();

            gzprintf(out_file, "Km:%.6f\t", analyzer.getGoodnessOfFitMetric()); 
    } catch (const std::exception& e) {
            std::cerr << "Error during Kmer Analysis: " << e.what() << std::endl;
            gzprintf(out_file, "\nError_in_Analysis\nKm= 0.000000\t");
    }
            gzprintf(out_file, "\n");
            for (int i = 0; i < sizeofy; ++i) 
                {    
                    for (int j = 0; j <sizeofx ; ++j) {
                        gzprintf(out_file, "%ld\t", count2[i][j]); 
                    }
                    gzprintf(out_file, "\n");
                }
                gzprintf(out_file, "\n");
}
for (size_t i = 0; i < fitted_models.size(); ++i) {
    for (size_t k = 0; k < fitted_models[i].size(); ++k) {
        gzprintf(out_file, "%.3f\t", fitted_models[i][k]);
    }
    gzprintf(out_file, "\n"); 
}
			deleteDirectory(folderName);
			gzclose(out_file); 
			kmer_data_base.Close();
			kmer_data_base1.Close();
			kmer_data_base3.Close();
			kmer_data_base4.Close();
            kmer_data_base5.Close();
			return EXIT_SUCCESS; 
}
// -------------------------------------------------------------------------
// Print execution options 
// -------------------------------------------------------------------------
void print_info(void)
{
	std::cout << "KMCG version 1.2-beta.\n"
			  << "\nUsage:\nKMCG [parameters] <KMC file prefix of raw data> <KMC file prefix of assembly> <Reference genome.fa> <output>\n";
}