SHELL := /bin/sh

# Load local secrets such as WORKER_URL and WORKER_TOKEN when present.
-include .env
export

GRADLE ?= ./gradlew
NPM ?= npm

ANDROID_IMAGE ?= ghcr.io/cirruslabs/android-sdk:35
ANDROID_DEV ?= android/dev.sh
ANDROID_RUN = SELFDROP_ANDROID_IMAGE=$(ANDROID_IMAGE) $(ANDROID_DEV)

TAILWIND_VERSION ?= 4.3.3
TAILWIND_INPUT ?= tailwind/input.css
TAILWIND_OUTPUT ?= src/main/resources/static/css/style.css
TAILWIND_CACHE ?= $(HOME)/.cache/selfdrop/tailwind-$(TAILWIND_VERSION)

.DEFAULT_GOAL := help

.PHONY: help tailwind-deps css css-watch build test check run dev image image-full clean clean-data clean-tailwind \
	android-image android-build android-release android-test android-lint android-install android-clean android-shell

## Show the available targets.
help:
	@echo "Selfdrop"
	@echo ""
	@echo "Usage: make <target>"
	@echo ""
	@echo "  make css             Regenerate the committed Tailwind CSS file"
	@echo "  make css-watch       Regenerate CSS while editing templates or app.js"
	@echo "  make build           Compile, test, and assemble the application"
	@echo "  make test            Run the test suite"
	@echo "  make check           Alias for test"
	@echo "  make run             Start the application on http://localhost:8080/"
	@echo "  make dev             Run css-watch and the application together"
	@echo "  make image           Build the jar, then the Docker image"
	@echo "  make image-full      Build the Docker image entirely inside Docker"
	@echo "  make android-image   Pull the Android SDK build image"
	@echo "  make android-build   Compile the Android debug APK in Docker"
	@echo "  make android-release Compile the Android release APK in Docker"
	@echo "  make android-test    Run Android unit tests in Docker"
	@echo "  make android-lint    Run Android lint in Docker"
	@echo "  make android-install Install the debug APK on a USB device (Linux)"
	@echo "  make android-shell   Open a shell in the Android build image"
	@echo "  make android-clean   Remove Android build output"
	@echo "  make clean           Remove Gradle build output"
	@echo "  make clean-data      Remove the local SQLite database and stored files"
	@echo "  make clean-tailwind  Remove the cached Tailwind CLI"
	@echo ""
	@echo "Variables: GRADLE, NPM, TAILWIND_VERSION, TAILWIND_INPUT,"
	@echo "           TAILWIND_OUTPUT, TAILWIND_CACHE, ANDROID_IMAGE"

## Install the Tailwind CLI into a cache directory outside the repository.
tailwind-deps:
	@if [ ! -x "$(TAILWIND_CACHE)/node_modules/.bin/tailwindcss" ]; then \
		echo "Installing Tailwind CLI $(TAILWIND_VERSION) into $(TAILWIND_CACHE)"; \
		$(NPM) install --prefix "$(TAILWIND_CACHE)" \
			--no-save --no-package-lock --silent \
			tailwindcss@$(TAILWIND_VERSION) @tailwindcss/cli@$(TAILWIND_VERSION); \
	fi

## Regenerate the committed Tailwind CSS file.
css: tailwind-deps
	NODE_PATH="$(TAILWIND_CACHE)/node_modules" \
		"$(TAILWIND_CACHE)/node_modules/.bin/tailwindcss" \
		-i $(TAILWIND_INPUT) \
		-o $(TAILWIND_OUTPUT) \
		--minify

## Regenerate CSS while editing templates or app.js.
css-watch: tailwind-deps
	NODE_PATH="$(TAILWIND_CACHE)/node_modules" \
		"$(TAILWIND_CACHE)/node_modules/.bin/tailwindcss" \
		-i $(TAILWIND_INPUT) \
		-o $(TAILWIND_OUTPUT) \
		--watch

## Compile, test, and assemble the application.
build:
	$(GRADLE) build

## Run the test suite.
test:
	$(GRADLE) test

## Alias for test.
check: test

## Start the application.
run:
	$(GRADLE) bootRun

## Run css-watch and the application together.
dev:
	$(MAKE) -j2 css-watch run

## Build the jar on the host, then the Docker image that packages it.
image:
	$(GRADLE) bootJar
	docker compose build

## Build the image entirely inside Docker (needs network; CI/fresh machine).
image-full:
	docker build -f Dockerfile.build -t selfdrop:local .

## Pull the Android SDK image used for containerized builds.
android-image:
	docker pull $(ANDROID_IMAGE)

## Compile the Android debug APK inside the Docker build image.
android-build:
	$(ANDROID_RUN) ./gradlew --no-daemon :app:assembleDebug

## Compile the Android release APK inside the Docker build image.
android-release:
	$(ANDROID_RUN) ./gradlew --no-daemon :app:assembleRelease

## Run the Android unit tests inside the Docker build image.
android-test:
	$(ANDROID_RUN) ./gradlew --no-daemon :app:testDebugUnitTest

## Run Android lint inside the Docker build image.
android-lint:
	$(ANDROID_RUN) ./gradlew --no-daemon :app:lintDebug

## Install the debug APK on a USB-connected device (Linux, adb inside Docker).
android-install:
	android/install.sh

## Open an interactive shell in the Android build image.
android-shell:
	$(ANDROID_RUN) bash

## Remove Android build output.
android-clean:
	$(ANDROID_RUN) ./gradlew --no-daemon clean

## Remove Gradle build output.
clean:
	$(GRADLE) clean

## Remove the local SQLite database and stored files.
clean-data:
	rm -rf data

## Remove the cached Tailwind CLI.
clean-tailwind:
	rm -rf "$(TAILWIND_CACHE)"
