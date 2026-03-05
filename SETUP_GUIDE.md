# SETUP GUIDE for Bazzite GPU Optimization on GTX 1660 Ti

This guide will walk you through the steps necessary to set up your GTX 1660 Ti for Bazzite GPU optimization.

## 1. Installing NVIDIA Drivers
   - Download the latest drivers from the [NVIDIA Driver Downloads](https://www.nvidia.com/Download/index.aspx) page.
   - Select your GPU model (GTX 1660 Ti) and your operating system.
   - Follow the instructions provided on the download page to install the drivers.

## 2. Installing Dependencies
   - Make sure you have the latest version of Python installed. You can download it from [python.org](https://www.python.org/downloads/).
   - Install other dependencies like Git and any additional libraries as recommended in the Bazzite documentation.

## 3. Running optimize_config.sh
   - Open a terminal window.
   - Navigate to the directory where the `optimize_config.sh` script is located.
   - Run the script using the following command:
     ```bash
     bash optimize_config.sh
     ```

## 4. Installing requirements-bazzite.txt
   - In the same terminal window, run:
     ```bash
     pip install -r requirements-bazzite.txt
     ```

## 5. Verifying GPU Setup
   - To ensure that your GPU is properly set up, you can use the NVIDIA SMI tool. Run the following command:
     ```bash
     nvidia-smi
     ```
   - You should see information about your GPU, including its utilization and memory.

## 6. Starting the Application
   - Once everything is installed, you can start the application with:
     ```bash
     python your_application.py
     ```

## 7. Testing Performance
   - Use the built-in performance testing tools to gauge how well the application runs on your GPU.
   - Document any results you notice during this testing phase.

## 8. Troubleshooting Common Issues
   - If you encounter issues with your setup, refer to the troubleshooting section of the Bazzite documentation. Common issues might include:
     - Driver installation problems
     - Python package conflicts
   - Check online forums or the Bazzite support for solutions to these issues.

## 9. Performance Benchmarks
   - After setting up, run benchmarks to test your GPU's performance with Bazzite.
   - Record the results for future reference and optimization efforts.