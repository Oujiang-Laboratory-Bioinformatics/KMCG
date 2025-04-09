**This is an introduction of KMCG, with human T2T assembly CHM13v2 as an example. Both the assembly and raw reads have a very large size, please download them from the public database.**

* Step 1 Download CHM13v2 reference genome

  The raw data used to generate CHM13v2 does not include the Y chromosome, so we should download the 'noY' version of the assembly.
```
wget https://s3-us-west-2.amazonaws.com/human-pangenomics/T2T/CHM13/assemblies/analysis_set/chm13v2.0_noY.fa.gz
gzip -d chm13v2.0_noY.fa.gz
samtools faidx chm13v2.0_noY.fa
```
* Step 2 Download CHM13v2 raw PacBio Hifi data from SRA
```
wget https://sra-downloadb.be-md.ncbi.nlm.nih.gov/sos4/sra-pub-zq-1/SRR011/11292/SRR11292120/SRR11292120.lite.1
wget https://sra-downloadb.be-md.ncbi.nlm.nih.gov/sos4/sra-pub-zq-1/SRR011/11292/SRR11292121/SRR11292121.lite.1
wget https://sra-downloadb.be-md.ncbi.nlm.nih.gov/sos4/sra-pub-zq-1/SRR011/11292/SRR11292122/SRR11292122.lite.1
wget https://sra-downloadb.be-md.ncbi.nlm.nih.gov/sos4/sra-pub-zq-1/SRR011/11292/SRR11292123/SRR11292123.lite.1
```
* Step 3 transfer SRA format into fastq.gz format
  
  This step needs The NCBI [SRA](https://github.com/ncbi/sra-tools) tools, or [parallel-fastq-dump](https://github.com/rvalieris/parallel-fastq-dump)
```
for files in $(ls SRR*); do parallel-fastq-dump --tmpdir . --threads 8 --gzip ${files}; done
```
* Step 4 generate KMC files for both raw data and assembly with KMC3
```
ls -1 SRR*.gz > files.lst
kmc -k31 -t32 -m490 -sm -fq -ci9 -cs16700000 -v @files.lst raw_CHM13 ./
kmc -k31 -t32 -m190 -sm -fm -ci1 -cs65535 -v ./chm13v2.0.fa asm_CHM13 ./
```
* Step 5 run KMCG, generate graph with step 10 in X axis. i.e., each block at the X-axis is a sum-up of 10 columns.
```
KMCG -xstep10 -p10 -t1000 ./raw_CHM13 ./asm_CHM13 ./chm13v2.0_noY.fa ./Hifi_CHM13v2noY.x1y10.kmcg
```
* Step 6 visualize the data with KMCGviewer. There is a copy of kmcg file in the folder. Your result should be similar to this file. Unzip before opening.
