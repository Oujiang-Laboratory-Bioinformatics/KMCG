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
#include <algorithm>
#include <random>
#include <zlib.h>  
#include <omp.h>
#include <thread> 

#include <condition_variable>



#include <memory>
#include <functional>





#include <mutex>

#include "./FM-index/FM-Index-master/FM.h"
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
    return (ext == "fa") || (ext == "fasta");
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
	int kmerlength=31;
	std::string CHR = "";
	std::string sex = "";
	std::string fragment = "";
	std::string input_file_name;
    std::string input_file_name1;
	std::string input_file_name2;
	std::string input_file_name3;
	std::string output_file_name;
    // FILE * out_file;
	gzFile  out_file;
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
	double kii;
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
            CHR = argv[i + 1];
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
                std::cerr << "Error: Missing value for -1 option." << std::endl;
                exit(EXIT_FAILURE);
            }
            fragment = argv[i + 1];
            i += 2;
        }
        else if (strcmp(argv[i], "-c") == 0) {
            gc = true;
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
                std::cerr << "Error: Missing value for -W option." << std::endl;
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
	std::vector<std::vector<std::vector<std::string>>> kmer2chr(sizeofy, std::vector<std::vector<std::string>>(sizeofx));
	vector<vector<uint64>> count(sizeofy, vector<uint64>(sizeofx));
	vector<vector<uint64>> countstep1(521, vector<uint64>(130));
	std::vector<std::vector<uint64>> kmernumofbox(ybox, std::vector<uint64>(xbox, 0));
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
    if (file.is_open()) {//从fai文件提取染色体及其长度信息
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
        if (sortedChrnumber[i]<1000000)
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
	while (std::getline(file1, line1)) {
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
	for (size_t i = 0; i < sequences.size(); ++i) {
		chrchr[i] = sequences[i].c_str();
	}
	const char** chrnamechr = new const char*[sequenceNames.size()];
	for (size_t i = 0; i < sequenceNames.size(); ++i) {
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
	for (int t = 0; t < num_strings; ++t) 
	{
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





























	if((out_file = gzopen (output_file_name.c_str(),"wb")) == NULL)
	{   
		std::cerr << "Failed to open output gz file."  << std::endl;
		print_info();
		return EXIT_FAILURE;
	}


	gzprintf(out_file,"KMCG1\t%u\t%u\t",ybox,xbox);
	// gzprintf(out_file,"%ld\t",SelectedchrArray.size());
	gzprintf(out_file,"%d\n",kmerlength);
	for (size_t i = 0; i < sortedChr1.size(); ++i) {
		gzprintf(out_file,"%s\t",sortedChr1[i].c_str());
	}
	for (size_t i = 0; i < sortedChr2.size(); ++i) {

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

		if((int)ceil((double)sortedChrnumber1[i] / tt)>999)
		{
			tt=tt*10;
		}
	}
	for (size_t i = 0; i < sortedChr1.size(); ++i) {

		gzprintf(out_file, "%d\t", (int)ceil((double)sortedChrnumber1[i] / tt));

	}
	for (size_t i = 0; i < (sortedChr2.size()/100); ++i) {
		gzprintf(out_file,"100\t");
	}
	if(sortedChr2.size()%100!=0)
	{
		gzprintf(out_file,"%zu",(sortedChr2.size()%100));
	
	}
	gzprintf(out_file,"\n");
		// setvbuf(out_file, NULL ,_IOFBF, 1 << 24);
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
				if(gc)
				{
					while (kmer_data_base1.ReadNextKmer(kmer_object, counter))
					{
					if (readtime%p==0)
					{
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
							count[0][y]++;
							// kmernumofbox[0][y]++;
							appendWordToCell(array[0][y],kmer_object.to_string().c_str(),kmernumofbox[0][y],gen);
							countstep1[0][counter > 128 ? 129 : counter]++;	
							int count_gc = 0;
							std::string kmer_str = kmer_object.to_string();  // 提前保存字符串
							for (char ch : kmer_str) {                      // 遍历已保存的字符串
								if (ch == 'G' || ch == 'g' || ch == 'C' || ch == 'c') {
									count_gc++;
								}
							}
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
				else
				{
					while (kmer_data_base1.ReadNextKmer(kmer_object, counter))
					{
					if (readtime%p==0)
					{
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
							count[0][y]++;
							// kmernumofbox[0][y]++;
							appendWordToCell(array[0][y],kmer_object.to_string().c_str(),kmernumofbox[0][y],gen);
							countstep1[0][counter > 128 ? 129 : counter]++;	
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

				if(gc)
				{
					while (kmer_data_base4.ReadNextKmer(kmer_object, counter))
					{
					if (readtime%p==0)
					{
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
							count[y][x]++;   
							appendWordToCell(array[y][x],kmer_object.to_string().c_str(),kmernumofbox[y][x],gen);      
							countstep1[counter < 513 ? counter : 512+findN(counter)-9][isInteger(counter1/(counter*1.0))? ((counter1/counter)+1<129? (counter1/counter)+1:129): (ceil(counter1/counter*1.0)<129? ceil(counter1/counter*1.0):129)]++;
							int count_gc = 0;
							std::string kmer_str = kmer_object.to_string();  // 提前保存字符串
							for (char ch : kmer_str) {                      // 遍历已保存的字符串
								if (ch == 'G' || ch == 'g' || ch == 'C' || ch == 'c') {
									count_gc++;
								}
							}
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
							appendWordToCell(array[y][0],kmer_object.to_string().c_str(),kmernumofbox[y][0],gen);
							countstep1[counter < 513 ? counter : 512+findN(counter)-9][0]++;
							int count_gc = 0;
							std::string kmer_str = kmer_object.to_string();  // 提前保存字符串
							for (char ch : kmer_str) {                      // 遍历已保存的字符串
								if (ch == 'G' || ch == 'g' || ch == 'C' || ch == 'c') {
									count_gc++;
								}
							}
							gccount[y][0]+=count_gc;
						}
					
					}
					readtime++;
					}
				}
				else
				{
					while (kmer_data_base4.ReadNextKmer(kmer_object, counter))
					{
					if (readtime%p==0)
					{
						bool is_kmer_found = kmer_data_base3.CheckKmer(kmer_object, counter1);

					
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
							appendWordToCell(array[y][x],kmer_object.to_string().c_str(),kmernumofbox[y][x],gen);   
   
							countstep1[counter < 513 ? counter : 512+findN(counter)-9][isInteger(counter1/(counter*1.0))? ((counter1/counter)+1<129? (counter1/counter)+1:129): (ceil(counter1/counter*1.0)<129? ceil(counter1/counter*1.0):129)]++;
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
							appendWordToCell(array[y][0],kmer_object.to_string().c_str(),kmernumofbox[y][0],gen);
							countstep1[counter < 513 ? counter : 512+findN(counter)-9][0]++;
						}
					
					}
					readtime++;
					}
					
				}
			}
			}
			kmer_data_base3.Close();
			kmer_data_base4.Close();
			
			for (int i = 0; i < sizeofy; ++i) 
				{
					for (int j = 0; j <sizeofx ; ++j) {
						sumofcount+=count[i][j];
						gzprintf(out_file, "%ld\t", count[i][j]); 
					}
					gzprintf(out_file, "\n");
				}
				gzprintf(out_file, "\n");
			std::cout << "检查点4" << " 抵达成功.\n";
			 now0 = std::time(nullptr);
std::cout << "当前时间4: " << std::ctime(&now0);
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



#pragma omp parallel for collapse(2) schedule(dynamic)
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




    optimizedParallelWithEfficientIO(kmer2chr, out_file, ROWS, COLS);
    


				std::cout << "检查点6" << " 抵达成功.\n";
			 now0 = std::time(nullptr);
std::cout << "当前时间6: " << std::ctime(&now0);





				for (size_t i = 0; i < countstep1.size(); ++i) 
				{
					for (size_t j = 0; j < countstep1[i].size(); ++j) 
					{
						gzprintf(out_file, "%ld\t", countstep1[i][j]); 
					}
					gzprintf(out_file, "\n");
				}












    std::vector<std::vector<double>> normalized_vectors(521, std::vector<double>(130, 0.0));

    std::vector<std::vector<double>> gaussian_values(521, std::vector<double>(130, 0.0));
	
    // 用于累计统计 Km、Ki 的变量
    double kmer_num_under_curve = 0;
    double all_kmer_num = 0;

    // 全局变量
    double prev_mean = 0.0;
    double theta = 0.0;
    double base_sigma = 0.0;
    double percentage = 0.0;
    double sum_smaller_values = 0.0;
    std::vector<double> percentage_list;    // 百分比列表
    std::vector<size_t> stored_rows;          // 存储行号
    std::vector<double> stored_best_means;      // 存储各行计算出的 best_mean
    std::vector<size_t> stored_lefts;           // 存储对应的 left 值
    std::vector<size_t> stored_rights;          // 存储对应的 right 值
    std::vector<double> stored_row_sums;        // 存储对应的 row_sum
	

    // 归一化
    for (size_t i = 0; i < countstep1.size(); ++i) {
        normalize_data(countstep1[i], normalized_vectors[i]);
    }
    
    // 检查数据
    if (normalized_vectors.size() <= 1) {
        throw std::out_of_range("Error: There are not enough rows to access.");
    }
    
    // 遍历每一行（忽略第0行，假设数据从第1行开始）
    for (size_t i = 1; i < countstep1.size(); ++i) {

        // 采用窗口法确定处理区间
        auto window = find_process_window(normalized_vectors[i], 0.5);
        size_t left = window.first;
        size_t right = window.second;
        
        if (i == 1) {
            double row_1_sum = std::accumulate(countstep1[1].begin(), countstep1[1].end(), 0.0);
            if (row_1_sum <= 0) {
                throw std::runtime_error("Error: The total sum of data in the row_1 is <= 0.");
            }

            double init_mean = find_initial_mean(normalized_vectors[i]);
            double init_sigma, dummy;
            std::tie(init_sigma, dummy) = find_initial_sigma(normalized_vectors[i], init_mean, left, right);
            double best_mean, best_sigma, min_error;
            std::tie(best_mean, best_sigma, min_error) = optimize_mean_and_sigma_grid_search(normalized_vectors[i], init_mean, init_sigma, left, right);
            
            theta = std::min(best_mean - left, right - best_mean);

            std::vector<double> temp_model(normalized_vectors[i].size(), 0.0);
            double final_sigma, row_sum_shrinkage, opt_percentage;
            std::tie(final_sigma, row_sum_shrinkage, opt_percentage) = optimize_sigma_and_row_sum(
                std::vector<double>(countstep1[i].begin(), countstep1[i].end()),
                temp_model,
                best_mean,
                best_sigma,
                row_1_sum,
                left,
                right
            );
            for (size_t j = 0; j < normalized_vectors[i].size(); ++j) {
                gaussian_values[i][j] = std::ceil(normalPDF(j, best_mean, final_sigma) * row_1_sum * row_sum_shrinkage);
            }
            sum_smaller_values = 0.0;
            for (size_t j = 0; j < normalized_vectors[i].size(); ++j) {
                sum_smaller_values += std::min(gaussian_values[i][j], static_cast<double>(countstep1[i][j]));
            }
            kmer_num_under_curve += sum_smaller_values;
            percentage = (sum_smaller_values / row_1_sum) * 100;
            percentage_list.push_back(percentage);
             
            prev_mean = best_mean;    // 保存第二行拟合的均值
            base_sigma = final_sigma;  // 保存第二行得到的 final_sigma
        }
        else {
            // 后续行：仅计算当前行的 best_mean（加权平均）
            double init_mean = prev_mean; // 使用上一行的 best_mean 作为初始值
            double left_temp = std::floor(init_mean - (theta / sqrt(i)));
            left = (left_temp < 0) ? 0 : static_cast<size_t>(left_temp);
            double right_temp = std::floor(init_mean + (theta / sqrt(i)));
            right = static_cast<size_t>(right_temp) + 1;
            if (right >= normalized_vectors[i].size())
                right = normalized_vectors[i].size() - 1;
            
            double weightedSum = 0.0;
            double dataSum = 0.0;
            for (size_t j = left; j <= right; ++j) {
                weightedSum += j * countstep1[i][j];
                dataSum += countstep1[i][j];
            }
            double best_mean = (dataSum != 0.0) ? weightedSum / dataSum : init_mean;
            prev_mean = best_mean; // 更新 prev_mean 供下一行使用
            
            // 将当前行的 best_mean 及相关信息保存
            stored_rows.push_back(i);
            stored_best_means.push_back(best_mean);
            stored_lefts.push_back(left);
            stored_rights.push_back(right);
            double row_sum = std::accumulate(countstep1[i].begin() , countstep1[i].end(), 0.0);
            stored_row_sums.push_back(row_sum);
        }
        
        // 统计累计：更新 all_kmer_num 与 kmer_num_under_curve
        for (size_t j = 0; j < countstep1[i].size(); ++j) {
            all_kmer_num += countstep1[i][j];
        }

    }
    double sum_means = std::accumulate(stored_best_means.begin(), stored_best_means.end(), 0.0);
    double final_mean = sum_means / stored_best_means.size();

    for (size_t idx = 0; idx < stored_rows.size(); ++idx) {
        size_t i = stored_rows[idx];
        double row_sum = stored_row_sums[idx];
        if (row_sum <= 0.0) {
            if (percentage_list.empty()) {
                percentage = 0.0;
            } else {
                percentage = percentage_list.back();
            }
            percentage_list.push_back(percentage);
            continue;
        }

        double sigma = base_sigma / sqrt(i);
        for (size_t j = 0; j < normalized_vectors[i].size(); ++j) {
            gaussian_values[i][j] = std::ceil(normalPDF(j, final_mean, sigma) * row_sum);
        }
        
        sum_smaller_values = 0.0;
        for (size_t j = 0; j < normalized_vectors[i].size(); ++j) {
            sum_smaller_values += std::min(gaussian_values[i][j], static_cast<double>(countstep1[i][j]));
        }
        kmer_num_under_curve += sum_smaller_values;
        double percentage = (sum_smaller_values / row_sum) * 100;
        percentage_list.push_back(percentage);

    }
    
    // 对第 0 行数据只统计（不拟合）
    for (size_t j = 0; j < countstep1[0].size(); ++j) {
        all_kmer_num += countstep1[0][j];
    }

    // 检查 all_kmer_num 是否为 0
    if (all_kmer_num == 0) {
        std::cerr << "Error: Total k-mer count (all_kmer_num) is zero." << std::endl;
        return 1;
    }
    
    // 如果 kmer_num_under_curve 与 all_kmer_num 恰好（或几乎）相等，则发出警告并停止 Km 的计算
    double ratio = kmer_num_under_curve / all_kmer_num;
    if (std::fabs(ratio - 1.0) < 1e-6) {
        std::cerr << "Warning: kmer_num_under_curve is equal (or nearly equal) to all_kmer_num. Cannot compute Km." << std::endl;
        return 1;
    }
	std::cout << "点5.4 " << "\n";
    double Km = 1 / (1 - ratio);
	gzprintf(out_file,"\n");
	for (double p : percentage_list) {
		gzprintf(out_file, "%.2f\t", p);
	}
	gzprintf(out_file, "\nKm= %.6f\t", Km);









    // 取出第一行数据
	// std::vector<unsigned int> data_row = countstep1[1]; 
	std::vector<unsigned long> data_row = countstep1[1];
    if (data_row.empty()) {
        std::cerr << "Error: The first row of countstep1 is empty." << std::endl;
        return 1;
    }
    
    // 对数据进行归一化处理
    std::vector<double> normalized_row(data_row.size(), 0.0);
    normalize_data(data_row, normalized_row);
    
    // 利用归一化数据计算初始均值和确定处理窗口
	size_t initial_mean = find_initial_mean(normalized_row);
    std::pair<size_t, size_t> window = find_process_window(normalized_row, 0.5);
    size_t left = window.first;
    size_t right = window.second;
    
    // 根据初始均值和窗口范围寻找初始 σ
    double initial_sigma, initial_min_error;
    std::tie(initial_sigma, initial_min_error) = find_initial_sigma(normalized_row, initial_mean, left, right);
    
    // 使用网格搜索进一步优化均值和 σ
    double best_mean, best_sigma, grid_error;
    std::tie(best_mean, best_sigma, grid_error) = optimize_mean_and_sigma_grid_search(normalized_row, initial_mean, initial_sigma, left, right);
    
    // 将原始数据转换为 double 型（用于后续优化）
    std::vector<double> original_vector(data_row.begin(), data_row.end());
    
    // 构建用于优化的高斯值向量，与数据行大小相同
    std::vector<double> gaussian_vector(data_row.size(), 0.0);
    // 计算行和（这里采用整行数据的累积和，可根据实际需求调整）
    double row_sum = std::accumulate(data_row.begin(), data_row.end(), 0.0);
    for (size_t i = 0; i < gaussian_vector.size(); ++i) {
        gaussian_vector[i] = normalPDF(i, best_mean, best_sigma) * row_sum;
    }
    
    // 进一步优化 σ 和行和缩放因子
    double  final_sigma,limit_percentage,row_sum_shrinkage;
    std::tie(final_sigma, row_sum_shrinkage, limit_percentage) = optimize_sigma_and_row_sum(
        original_vector, gaussian_vector, best_mean, best_sigma, row_sum, left, right
    );
    

	for(size_t i=std::max(static_cast<int>(std::floor(best_mean-3*final_sigma)), 11);i<102;i++)
	{
			sumforki0+=ki0[i];
	}

	for(size_t i=std::max(static_cast<int>(std::floor(best_mean-3*final_sigma)), 11);i<102;i++)
	{
			sumforki+=ki[i];
	}
	if (sumforki0 != 0 && (sumforki0 + sumforki) != 0)
	{
		kii=1/(1-((sumforki*1.0)/(sumforki0+sumforki)));
		gzprintf(out_file, "Ki= %.6f\n", kii);
	}
	else if(sumforki0 == 0)
	{
	sumforki0=1;
	kii=1/(1-((sumforki*1.0)/(sumforki0+sumforki)));
	gzprintf(out_file, "Ki= %.6f\n", kii);	
	}
	else
	{
		std::cerr << "Error:  All data are equal to zero." << std::endl;
	}




		if(gc!=0)
		{
			for (int i = 0; i < sizeofy; ++i) 
				{
					for (int j = 0; j <sizeofx ; ++j) {
						sumofgccount+=gccount[i][j];
					}
				}
	
				gzprintf(out_file, "GCpercentage\t");
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

			deleteDirectory(folderName);
			gzclose(out_file); 
			kmer_data_base.Close();
			kmer_data_base1.Close();
			kmer_data_base3.Close();
			kmer_data_base4.Close();
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
