#!/usr/bin/env bash
# ============================================================
#  CoreGeek 《未来战争》编译脚本（Linux/macOS/Git-Bash）
#  用法：./build.sh
#  输出：bin/ 目录下的 .class 文件
# ============================================================
set -e
cd "$(dirname "$0")"

mkdir -p bin

cd src
javac -Xlint:unchecked -Xlint:-options -source 1.8 -target 1.8 \
  -encoding UTF-8 -cp "../lib/*" -d ../bin @../makelist.txt

echo "[BUILD OK] classes output to bin/"
