FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/build-tools/35.0.0:${PATH}"

# Install system dependencies
RUN apt-get update && apt-get install -y \
    openjdk-17-jdk-headless \
    wget \
    unzip \
    curl \
    git \
    && rm -rf /var/lib/apt/lists/*

# Install Gradle 8.9 (required by AGP 8.5+)
ENV GRADLE_HOME=/opt/gradle/gradle-8.9
ENV PATH="${GRADLE_HOME}/bin:${PATH}"
RUN wget -q "https://services.gradle.org/distributions/gradle-8.9-bin.zip" \
        -O /tmp/gradle.zip && \
    unzip -q /tmp/gradle.zip -d /opt/gradle && \
    rm /tmp/gradle.zip

# Download and install Android SDK command-line tools
RUN mkdir -p "${ANDROID_HOME}/cmdline-tools" && \
    wget -q "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" \
        -O /tmp/cmdline-tools.zip && \
    unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools-extracted && \
    mv /tmp/cmdline-tools-extracted/cmdline-tools "${ANDROID_HOME}/cmdline-tools/latest" && \
    rm -rf /tmp/cmdline-tools.zip /tmp/cmdline-tools-extracted

# Accept licenses and install required SDK packages
RUN yes | sdkmanager --licenses > /dev/null 2>&1 && \
    sdkmanager \
        "platforms;android-35" \
        "build-tools;35.0.0" \
        "platform-tools"

# Create working directory
WORKDIR /workspace

CMD ["/bin/bash"]
