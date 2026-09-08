# Rupiah_Vision

A personal self-learning project focused on building an Android application that recognizes Indonesian Rupiah banknote denominations using deep learning.

## Overview

Rupiah_Vision is an Android-based currency classification application that uses a trained deep learning model to recognize Indonesian Rupiah denominations from images captured through the device camera.

This project was created as a personal experiment to explore the process of integrating a computer vision model into a mobile application.

## Features

- Capture Indonesian Rupiah banknotes using the camera
- Classify banknote denominations using a deep learning model
- Run inference directly on the Android device
- No internet connection required during inference

## Tech Stack

- Android / Kotlin
- YOLOv8 Classification
- ONNX
- ONNX Runtime
- Roboflow
- Kaggle

## Dataset

The model was trained using a public dataset from Roboflow Universe:

**deteksi-rupiah-zen87**

The dataset contains 13,300 labeled images covering Indonesian Rupiah denominations from Rp1,000 to Rp100,000.

## Model

The classification model was trained using YOLOv8-cls (Ultralytics) and exported to ONNX format for integration with the Android application.

Inference is performed locally using ONNX Runtime.

## Project Status

Personal Project — In Development

The current version is an early APK build and is still being improved, particularly in terms of real-world classification accuracy.

## Purpose

This project is mainly built for learning and experimentation with the end-to-end computer vision workflow:

Dataset → Model Training → Testing → ONNX Export → Android Integration
