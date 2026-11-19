# Distributed under the OSI-approved BSD 3-Clause License.  See accompanying
# file Copyright.txt or https://cmake.org/licensing for details.

cmake_minimum_required(VERSION ${CMAKE_VERSION}) # this file comes with cmake

# If CMAKE_DISABLE_SOURCE_CHANGES is set to true and the source directory is an
# existing directory in our source tree, calling file(MAKE_DIRECTORY) on it
# would cause a fatal error, even though it would be a no-op.
if(NOT EXISTS "/home/shann/Documents/AndroidProj/StudyApp/app/.cxx/Release/1v1174o1/arm64-v8a/_deps/kleidiai-src")
  file(MAKE_DIRECTORY "/home/shann/Documents/AndroidProj/StudyApp/app/.cxx/Release/1v1174o1/arm64-v8a/_deps/kleidiai-src")
endif()
file(MAKE_DIRECTORY
  "/home/shann/Documents/AndroidProj/StudyApp/app/.cxx/Release/1v1174o1/arm64-v8a/_deps/kleidiai-build"
  "/home/shann/Documents/AndroidProj/StudyApp/app/.cxx/Release/1v1174o1/arm64-v8a/_deps/kleidiai-subbuild/kleidiai-populate-prefix"
  "/home/shann/Documents/AndroidProj/StudyApp/app/.cxx/Release/1v1174o1/arm64-v8a/_deps/kleidiai-subbuild/kleidiai-populate-prefix/tmp"
  "/home/shann/Documents/AndroidProj/StudyApp/app/.cxx/Release/1v1174o1/arm64-v8a/_deps/kleidiai-subbuild/kleidiai-populate-prefix/src/kleidiai-populate-stamp"
  "/home/shann/Documents/AndroidProj/StudyApp/app/.cxx/Release/1v1174o1/arm64-v8a/_deps/kleidiai-subbuild/kleidiai-populate-prefix/src"
  "/home/shann/Documents/AndroidProj/StudyApp/app/.cxx/Release/1v1174o1/arm64-v8a/_deps/kleidiai-subbuild/kleidiai-populate-prefix/src/kleidiai-populate-stamp"
)

set(configSubDirs )
foreach(subDir IN LISTS configSubDirs)
    file(MAKE_DIRECTORY "/home/shann/Documents/AndroidProj/StudyApp/app/.cxx/Release/1v1174o1/arm64-v8a/_deps/kleidiai-subbuild/kleidiai-populate-prefix/src/kleidiai-populate-stamp/${subDir}")
endforeach()
if(cfgdir)
  file(MAKE_DIRECTORY "/home/shann/Documents/AndroidProj/StudyApp/app/.cxx/Release/1v1174o1/arm64-v8a/_deps/kleidiai-subbuild/kleidiai-populate-prefix/src/kleidiai-populate-stamp${cfgdir}") # cfgdir has leading slash
endif()
