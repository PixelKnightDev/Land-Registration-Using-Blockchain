#!/bin/bash
# Script to Deploy the Land Registry Java Chaincode to the Fabric Network

set -e

# --- Configuration Variables ---
CHANNEL_NAME="landchannel"
CC_NAME="landregistry"
CC_SRC_PATH="../chaincode/land-contract-java"
CC_SRC_LANGUAGE="java"
CC_VERSION="1.0"
CC_SEQUENCE="1"
ORDERER_CA=${PWD}/crypto-config/ordererOrganizations/landregistry.com/orderers/orderer0.landregistry.com/msp/tlscacerts/tlsca.landregistry.com-cert.pem
ORDERER_ADDRESS="localhost:7050"

# --- Helper Function: Switch Environment Variables between Orgs ---
setGlobals() {
  local ORG=$1
  if [ "$ORG" -eq 1 ]; then
    export CORE_PEER_LOCALMSPID="Org1MSP"
    export CORE_PEER_TLS_ROOTCERT_FILE=${PWD}/crypto-config/peerOrganizations/org1.landregistry.com/peers/peer0.org1.landregistry.com/tls/ca.crt
    export CORE_PEER_MSPCONFIGPATH=${PWD}/crypto-config/peerOrganizations/org1.landregistry.com/users/Admin@org1.landregistry.com/msp
    export CORE_PEER_ADDRESS=localhost:7051
  elif [ "$ORG" -eq 2 ]; then
    export CORE_PEER_LOCALMSPID="Org2MSP"
    export CORE_PEER_TLS_ROOTCERT_FILE=${PWD}/crypto-config/peerOrganizations/org2.landregistry.com/peers/peer0.org2.landregistry.com/tls/ca.crt
    export CORE_PEER_MSPCONFIGPATH=${PWD}/crypto-config/peerOrganizations/org2.landregistry.com/users/Admin@org2.landregistry.com/msp
    export CORE_PEER_ADDRESS=localhost:9051
  else
    echo "Unknown Org"
    exit 1
  fi
  export CORE_PEER_TLS_ENABLED=true
}

echo "================================================="
echo "Starting Chaincode Deployment Lifecycle"
echo "================================================="

# 0. Create and Join the Channel
echo "Step 0: Waiting for Raft Leader Election (10s)..."
sleep 10

echo "Step 0: Creating and Joining the Channel..."
configtxgen -profile LandChannel -configPath ${PWD} -outputCreateChannelTx ./landchannel.tx -channelID ${CHANNEL_NAME}

setGlobals 1
peer channel create \
  -o ${ORDERER_ADDRESS} \
  --ordererTLSHostnameOverride orderer0.landregistry.com \
  -c ${CHANNEL_NAME} \
  -f ./landchannel.tx \
  --outputBlock ./landchannel.block \
  --tls --cafile ${ORDERER_CA}

peer channel join -b ./landchannel.block

setGlobals 2
peer channel join -b ./landchannel.block

# 1. Package the Java Chaincode
echo "Step 1: Packaging the Java Chaincode..."
pushd ${CC_SRC_PATH}
./gradlew clean build shadowJar
popd
peer lifecycle chaincode package ${CC_NAME}.tar.gz \
  --path ${CC_SRC_PATH}/build/libs/ \
  --lang ${CC_SRC_LANGUAGE} \
  --label ${CC_NAME}_${CC_VERSION}

# 2. Install Chaincode on Org1 and Org2
echo "Step 2: Installing Chaincode on both Organizations..."
setGlobals 1
peer lifecycle chaincode install ${CC_NAME}.tar.gz

setGlobals 2
peer lifecycle chaincode install ${CC_NAME}.tar.gz

# 3. Get the Package ID
setGlobals 1
peer lifecycle chaincode queryinstalled >&log.txt
PACKAGE_ID=$(sed -n "/${CC_NAME}_${CC_VERSION}/{s/^Package ID: //; s/, Label:.*$//; p;}" log.txt)
echo "Package ID determined as: ${PACKAGE_ID}"

# 4. Approve for Org1
echo "Step 3: Approving Chaincode Definition for Org1..."
setGlobals 1
peer lifecycle chaincode approveformyorg \
  -o ${ORDERER_ADDRESS} \
  --ordererTLSHostnameOverride orderer0.landregistry.com \
  --tls \
  --cafile ${ORDERER_CA} \
  --channelID ${CHANNEL_NAME} \
  --name ${CC_NAME} \
  --version ${CC_VERSION} \
  --package-id ${PACKAGE_ID} \
  --sequence ${CC_SEQUENCE}

# 5. Approve for Org2
echo "Step 4: Approving Chaincode Definition for Org2..."
setGlobals 2
peer lifecycle chaincode approveformyorg \
  -o ${ORDERER_ADDRESS} \
  --ordererTLSHostnameOverride orderer0.landregistry.com \
  --tls \
  --cafile ${ORDERER_CA} \
  --channelID ${CHANNEL_NAME} \
  --name ${CC_NAME} \
  --version ${CC_VERSION} \
  --package-id ${PACKAGE_ID} \
  --sequence ${CC_SEQUENCE}

# 6. Commit the Chaincode to the Channel
echo "Step 5: Committing the Chaincode to the Channel..."
peer lifecycle chaincode commit \
  -o ${ORDERER_ADDRESS} \
  --ordererTLSHostnameOverride orderer0.landregistry.com \
  --tls \
  --cafile ${ORDERER_CA} \
  --channelID ${CHANNEL_NAME} \
  --name ${CC_NAME} \
  --peerAddresses localhost:7051 --tlsRootCertFiles ${PWD}/crypto-config/peerOrganizations/org1.landregistry.com/peers/peer0.org1.landregistry.com/tls/ca.crt \
  --peerAddresses localhost:9051 --tlsRootCertFiles ${PWD}/crypto-config/peerOrganizations/org2.landregistry.com/peers/peer0.org2.landregistry.com/tls/ca.crt \
  --version ${CC_VERSION} \
  --sequence ${CC_SEQUENCE}

# 7. Initialize the Ledger
echo "Step 6: Initializing the Ledger..."
sleep 3
peer chaincode invoke \
  -o ${ORDERER_ADDRESS} \
  --ordererTLSHostnameOverride orderer0.landregistry.com \
  --tls \
  --cafile ${ORDERER_CA} \
  --channelID ${CHANNEL_NAME} \
  --name ${CC_NAME} \
  --peerAddresses localhost:7051 --tlsRootCertFiles ${PWD}/crypto-config/peerOrganizations/org1.landregistry.com/peers/peer0.org1.landregistry.com/tls/ca.crt \
  --peerAddresses localhost:9051 --tlsRootCertFiles ${PWD}/crypto-config/peerOrganizations/org2.landregistry.com/peers/peer0.org2.landregistry.com/tls/ca.crt \
  -c '{"function":"initLedger","Args":[]}'

echo "================================================="
echo "Deployment Complete! Chaincode is active."
echo "================================================="