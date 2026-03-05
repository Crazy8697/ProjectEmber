#!/bin/bash
echo "GPU detected:"
nvidia-smi
cp .env.example .env
pip install -r requirements-bazzite.txt
