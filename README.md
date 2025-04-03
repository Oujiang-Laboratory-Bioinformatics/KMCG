# KMCG
## Kmer-based tools to evaluate and improve T2T level genome assemblies
Eukaryotic, especially mammalian species, have a high ratio of repeats in their genome, these regions including centromeres, transponsal elements, and rRNA arrays. Any T2T level assembly must resolve them. Currently, evaluating the quality of these regions is very difficult.

KMCG (**K**mer **M**ultiplicity **C**onsensus **G**raph) provides a set of tools to compare the multiplicity of kmers between the genome assembly and raw data, which can be used to evaluate and improve the T2T level genome assemblies independently, without a high-quality reference.

## System Requirement
KMCG is designed for sequencing hubs with the ability of *de novo* assemblies of complex genomes. For a mammalian genome similar to humans, running the KMGC main script needs around 256GB of memory and 64 cores of CPU. Linux-based HPC with 512GB of memory is recommended. The code is compatible with workload managers like Slurm or LSF.   

However, processed data can be visualized by KMGC viewer, which is a JAVA-based programme accessable from any laptop or desttop computers.

## Dependency
  * [KMC 3.2](https://github.com/refresh-bio/KMC) 
  * Java run time environment (JRE)
  * Samtools
  * gcc 10.2.0 or higher

## Installation
### KMC
Download [KMC](https://github.com/refresh-bio/KMC) from website, install the pipeline. Get working KMC in your `PATH`. 
### KMCG
Download C++ source code.
```
git clone https://github.com/Oujiang-Laboratory-Bioinformatics/KMCG.git
cd KMCG

```

## Run
```
./KMCG [parameters] <KMC file prefix of raw data> <KMC file prefix of assembly> <Reference genome.fa> <Reference genome.fa.fai> <output> 
-xstep1 -ystep1  -p1 -t1000
```
