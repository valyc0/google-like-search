#!/bin/bash

# Script per fermare tutti i servizi

echo "Stopping Google-like Search..."

# Ferma il processo DocumentSearchApplication
pkill -f "DocumentSearchApplication" 2>/dev/null || true

echo "All services stopped!"
