# KMCG
A Kmer-based tools to evaluate and improve genome assemblies

## Build

### Linux (Using Ubuntu 22.04 as example)

```sh
sudo apt install -y git openjdk-11-jdk maven
git clone git@github.com:Oujiang-Laboratory-Bioinformatics/KMCG.git
cd ./KMCG
mvn package
```

### Windows



```pwsh
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
Invoke-RestMethod -Uri https://get.scoop.sh | Invoke-Expression
scoop bucket add extras
scoop install git innosetup
git clone git@github.com:Oujiang-Laboratory-Bioinformatics/KMCG.git
cd .\KMCG
mvn package
```
