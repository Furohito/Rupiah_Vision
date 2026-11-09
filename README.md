# Rupiah Vision

Rupiah Vision is an Android app designed to recognize Indonesian Rupiah banknotes using a deep learning model running directly on the device.

## Main Function

The app's main purpose is to identify the denomination of a banknote by analyzing an image captured from the phone camera. The user points the camera at a banknote, and the app predicts its value, such as Rp1.000, Rp2.000, Rp5.000, Rp10.000, Rp20.000, Rp50.000, or Rp100.000.

This project focuses on integrating computer vision and artificial intelligence into a mobile application so the recognition process happens locally on Android without needing a server.

## How It Works

1. The user opens the app and points the camera at a Rupiah banknote.
2. The app captures the image.
3. A trained deep learning model processes the image locally on the device.
4. The model predicts the banknote denomination.
5. The prediction result is displayed to the user.

## Features

- Camera-based Rupiah banknote recognition
- Local banknote classification using AI
- On-device inference with ONNX Runtime
- No internet connection required during recognition
- Android app built with Kotlin

## Tech Stack

- Android
- Kotlin
- YOLOv8 Classification
- ONNX
- ONNX Runtime
- Roboflow
- Kaggle

## Dataset

The model was trained using the public Roboflow dataset named `deteksi-rupiah-zen87`.

This dataset contains labeled images of various Indonesian Rupiah notes and covers denominations from Rp1.000 to Rp100.000.

## Model

The project uses a YOLOv8 classification model trained for banknote recognition and exported to ONNX format for Android deployment.

Inference is performed locally using ONNX Runtime, allowing the app to classify banknotes directly on the device.

## Project Purpose

This is a personal self-learning project focused on the end-to-end workflow of AI-powered mobile development:

Dataset collection → Model training → Model testing → ONNX export → Android integration → On-device inference

The main goal is to learn how to combine computer vision, deep learning, and Android application development into a practical real-world app.

## Project Status

Personal project — in development.

The current app is still being improved, and accuracy may vary depending on image quality, lighting, camera angle, background, and the condition of the banknote.

## Notes

This project is intended for learning and experimentation. It is not a production-grade financial validation system and should not be treated as a financial or authentication tool.
