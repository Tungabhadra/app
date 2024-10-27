#
# Copyright (c) 2018, 2019, 2023-2024 Qualcomm Technologies, Inc.
# All Rights Reserved.
# Confidential and Proprietary - Qualcomm Technologies, Inc.
#

#############################################################
# Inception V3 setup
#############################################################

mkdir -p inception_v3

cd model

cp ../../model.dlc model.dlc

zip -r model.zip ./*
mkdir -p ../app/src/main/res/raw/
cp model.zip ../app/src/main/res/raw/

cd ..
rm -rf ./model
