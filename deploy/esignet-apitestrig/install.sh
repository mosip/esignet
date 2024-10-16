#!/bin/bash

# Prompt the user for the branch name
read -p "Enter the Git branch (default is 'develop'): " GIT_BRANCH
GIT_BRANCH=${GIT_BRANCH:-develop}  # Default to 'develop' if no input is provided

echo "set kubeconfig"
export KUBECONFIG=$1

# Set variables
REPO_URL="https://github.com/mosip/mosip-functional-tests.git"
INSTALL_SCRIPT="install.sh"
VALUES_FILE="../../../values.yaml"  # Path to your custom values.yaml file

# Clone the repository
echo "Cloning repository from $REPO_URL on branch $GIT_BRANCH..."
if git clone -b "$GIT_BRANCH" "$REPO_URL" ; then
    echo "Repository cloned successfully."
else
    echo "Failed to clone repository."
    exit 1
fi

# Navigate to the deployment directory
echo "Navigating to deploy directory..."
git_repo_name="$(basename "$REPO_URL" .git)"

cd $git_repo_name

git sparse-checkout init --cone && git sparse-checkout set deploy/apitestrig

find . -type f ! -path "./deploy/*" -exec rm -f {} \;

cd deploy/apitestrig || { echo "apitestrig directory not found."; exit 1; }
cp $VALUES_FILE values.yaml

# Check if the install script exists and is executable
if [ -x "$INSTALL_SCRIPT" ]; then
    echo "Running install script with values file $VALUES_FILE..."
    source ./"$INSTALL_SCRIPT"
    echo "Install script executed successfully."
else
    echo "Install script not found or not executable."
    exit 1
fi

# Cleanup cloned repository
echo "Cleaning up..."
cd ../../..
rm -rf $git_repo_name

echo "Deployment complete!"
