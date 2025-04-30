# KMCG Viewer

## Build & Packaging

### Prerequisites

#### Linux

OpenJDK 21, Apache Maven, and Git are required to build the project. On Debian or Ubuntu-derived Linux distributions you can install required packages by:

```sh
sudo apt install -y git maven openjdk-21-jdk 
```

#### Windows

OpenJDK 21, Apache Maven, Inno Setup, and Git are required to build the project. Suppose that you are [Scoop](https://scoop.sh) package manager installed, if not, execute the following command in PowerShell:

```pwsh
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
Invoke-RestMethod -Uri https://get.scoop.sh | Invoke-Expression
scoop install git
```

Then, install the required tools:

```pwsh
scoop bucket add extras
scoop bucket add java
scoop install maven microsoft21-jdk inno-setup
```

#### macOS

OpenJDK 21, Apache Maven, and Git are required to build the project. Suppose that you have [Homebrew](https://brew.sh) package manager available in your system, you can install required tools execute the following command in terminal:

```sh
brew install git maven microsoft-openjdk@21
```

### Packaging

To package KMCG Viewer, run:

```sh
git clone git@github.com:Oujiang-Laboratory-Bioinformatics/KMCG.git
cd ./KMCG/KMCG_Viewer
mvn package
```

The packaged AppImage / EXE / DMG will be available under `target` directory.
