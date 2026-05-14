
# Project Specification

## Project Scope

The scope of this project is to create a simple Android application that captures real time audio from the micrphone(s) / audio input devices of the
smartphone and replays it immediately through a sound output device (primary target are bluetooth speakers, but could also be wired type-c to AUX or AUX outputs).

## Permissions

The application should request all required permission upon first boot, and on subsequent boots verify they are given. If not,
it should prompt the user again. The permissions should allow the app to access the Bluetooth speaker (or any sound output device) and the microphone.

## Development toolchain

The goal here is to keep my host machine intact. This means that the entire development and build toolchain should be containerized
using Docker images. I do not want to install Android studio or any other tool. Only use tools available in Docker images. A reference Dockerfile (probavly good to use as-is)
and a reference Makefile can be found under the reference directory of the current project.

## Android Compatibility

This app should try to be compatible with as many Android versions as possible. If this is not possible, it should primarily target my phone, One Plus Nord 5, running Oxygen OS 15.

## Icons, color schemas, graphics

Feel free to choose any color schema you find suitable. Download any icons you might need from widely known free vendors off the
internet. A microphone or a Megaphone could be suitable.

## UI

The UI should be a simple single page app, that has a toggle button to turn on / off the replay. a dropdown menu that can help the user choose between all the available outputs should
also be included if applicable. same goes for available inputs.

## Language

The programming language to be used is up to you to decide. Feel free to use any language seems simpler or more suiting for this task.

## Deliverables

We need to have the following deliverables:

- The source code of the application
- A custom Dockerfile based on the reference one that will be used to develop and build this application (verify that reusing the reference Dockerfile is not enough before attempting to create a new one)
- A Makefile that utilizes docker to run and build the application
- One or more APK files used to install the application (if applicable for different Android version build target)
- A simple and short README.md that explains what the application does and how to build it
