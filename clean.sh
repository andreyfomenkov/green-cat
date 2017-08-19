#!/bin/sh
clear
rm -rf /home/afomenkov/workspace/client-android/build/greencat/copy
cp -r /home/afomenkov/workspace/client-android/build/greencat/lambda /home/afomenkov/workspace/client-android/build/greencat/copy
echo "Number of files before:"
find /home/afomenkov/workspace/client-android/build/greencat/copy -name "*.class" | wc -l

echo "Cleanup..."
find /home/afomenkov/workspace/client-android/build/greencat/copy -type f ! \( -name 'LoginFragment.class' -o -name 'LoginFragment$*.class' \) -print0 | xargs -0 rm --

echo "Number of files after:"
find /home/afomenkov/workspace/client-android/build/greencat/copy -name "*.class" | wc -l

find /home/afomenkov/workspace/client-android/build/greencat/copy -name "*.class"
