#!/bin/bash

# Find the process ID (PID) of the SendMsg process.
pid=$(ps aux | grep "SendMsg" | grep "app_process" | grep -v "sh -c" | awk '{print $2}')

# Check if the PID is found.
if [ -n "$pid" ]; then
  # Kill the process.
  sudo kill -9 "$pid"
  # Check if the kill command was successful.
  if [ $? -eq 0 ]; then
    echo "SendMsg(PID: $pid) is killed..."
  else
    echo "Failed to kill SendMsg(PID: $pid)."
  fi
else
  echo "SendMsg is not running..."
fi
