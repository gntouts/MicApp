IMAGE_NAME  := mic-app-builder
CONTAINER_NAME := mic-app-build
OUTPUT_DIR   := output
APK_SRC      := app/build/outputs/apk/debug/app-debug.apk
APK_DST      := $(OUTPUT_DIR)/mic-app.apk
# Use a project-local cache to avoid lock conflicts with any host Gradle daemon
GRADLE_CACHE := $(PWD)/.gradle-cache

.PHONY: image init build clean shell apk

## Build the Docker image (only needed once or after Dockerfile changes)
image:
	docker build -t $(IMAGE_NAME) .

## Bootstrap the Gradle wrapper jar (run once after cloning on a fresh checkout)
init: image
	docker run --rm \
		-v "$(PWD)":/workspace \
		-v "$(GRADLE_CACHE)":/root/.gradle \
		-w /workspace \
		$(IMAGE_NAME) \
gradle wrapper --gradle-version=8.9 --distribution-type=bin

## Build the debug APK inside Docker
build: image
	docker run --rm \
		-v "$(PWD)":/workspace \
		-v "$(GRADLE_CACHE)":/root/.gradle \
		-w /workspace \
		$(IMAGE_NAME) \
		bash -c "chmod +x gradlew && ./gradlew assembleDebug"

## Clean build artifacts inside Docker
clean: image
	docker run --rm \
		-v "$(PWD)":/workspace \
		-v "$(GRADLE_CACHE)":/root/.gradle \
		-w /workspace \
		$(IMAGE_NAME) \
		bash -c "chmod +x gradlew && ./gradlew clean"

## Open an interactive shell inside the build container
shell: image
	docker run --rm -it \
		-v "$(PWD)":/workspace \
		-v "$(GRADLE_CACHE)":/root/.gradle \
		-w /workspace \
		$(IMAGE_NAME) \
		/bin/bash

## Copy the built APK to ./output/
apk:
	mkdir -p $(OUTPUT_DIR)
	cp $(APK_SRC) $(APK_DST)
	@echo "APK available at: $(APK_DST)"
