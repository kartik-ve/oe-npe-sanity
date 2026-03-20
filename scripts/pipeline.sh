if [ -z "${ENV}" ]; then
  echo "ERROR: 'ENV' parameter not passed" >&2
  exit 1
fi

BUILD_DIR=${BUILD_NUMBER}

OMS_BASE=/users/gen/omswrk1/JEE/OMS/logs/OmsDomain/OmsServer
OMS_WORKSPACE=${OMS_BASE}/sanity_logs
OMS_BUILD=${OMS_WORKSPACE}/${JOB_NAME}_${BUILD_NUMBER}

SE_BASE=/users/gen/sewrk1/JEE/SEDomain/logs/SEServer
SE_WORKSPACE=${SE_BASE}/sanity_logs
SE_BUILD=${SE_WORKSPACE}/${JOB_NAME}_${BUILD_NUMBER}

# remove if fetching from git
cp -r /toolsnas_CHR/tooladm/OE/OE-NPE-SANITY/* .

declare -A HOSTS=(
  [SIT1]=mwhlvchca01
  [QA1]=mwhlvchca02
  [UAT1]=mwhlvchca03
  [HF1]=mwhlvchca04
)

HOST=${HOSTS[$ENV]}

ssh omswrk1@${HOST} \
  "ps -eo pid,etimes,cmd \
  | awk '\$2 >= 21600 && \$0 ~ /tail -fn 0 \/users\/gen\/omswrk1\/JEE\/OMS\/logs\/OmsDomain\/OmsServer\/weblogic/ {print \$1}' \
  | xargs -r kill" \
|| true

ssh omswrk1@${HOST} \
  "ps -eo pid,etimes,cmd \
  | awk '\$2 >= 21600 && \$0 ~ /tail -fn 0 \/users\/gen\/sewrk1\/JEE\/SEDomain\/logs\/SEServer\/weblogic/ {print \$1}' \
  | xargs -r kill" \
|| true

ssh omswrk1@${HOST} \
  "find ${OMS_WORKSPACE} -mindepth 1 -mmin +360 -exec rm -rf {} +"
ssh sewrk1@${HOST} \
  "find ${SE_WORKSPACE} -mindepth 1 -mmin +360 -exec rm -rf {} +"

set -e

ssh omswrk1@${HOST} "mkdir -p ${OMS_BUILD}"

scp java/remote/LogSearch.java omswrk1@${HOST}:${OMS_WORKSPACE}
ssh omswrk1@${HOST} "javac -d ${OMS_WORKSPACE} ${OMS_WORKSPACE}/LogSearch.java"

ssh sewrk1@${HOST} "mkdir -p ${SE_BUILD}"

scp java/remote/LogSearch.java sewrk1@${HOST}:${SE_WORKSPACE}
ssh sewrk1@${HOST} "javac -d ${SE_WORKSPACE} ${SE_WORKSPACE}/LogSearch.java"

PROJECT="${WORKSPACE}/xml/OE-Sanity.xml"
REPORT_DIR="${WORKSPACE}/${BUILD_NUMBER}/junit_report"

TESTSUITE_PREFIX=
if [ "${SANITY_TYPE}" == "Basic" ]; then
  TESTSUITE_PREFIX="Basic Sanity - "
fi

for S in NC COS CE RP Move Bulk SU COAM; do
  case "${S}" in
    NC)   TESTSUITE="New Connect" ;;
    COS)  TESTSUITE="Change of Service" ;;
    CE)   TESTSUITE="Cease & Restart" ;;
    RP)   TESTSUITE="Replace Offer" ;;
    Move) TESTSUITE="Move & Transfer" ;;
    Bulk) TESTSUITE="Bulk Tenant" ;;
    SU)   TESTSUITE="Seasonal Suspend" ;;
    COAM)
      if [ "${SANITY_TYPE}" != "Basic" ]; then
        continue
      fi
      TESTSUITE="CO & AM"
      ;;
  esac

  echo Running flow ${TESTSUITE}

  ssh omswrk1@${HOST} \
    "tail -fn 0 \$(ls -t ${OMS_BASE}/weblogic.*.log | head -1) \
      > ${OMS_BUILD}/${S}.log 2>&1 & echo \$! > ${OMS_BUILD}/${S}.pid"
  
  ssh sewrk1@${HOST} \
    "tail -fn 0 \$(ls -t ${SE_BASE}/weblogic.*.log | head -1) \
      > ${SE_BUILD}/${S}.log 2>&1 & echo \$! > ${SE_BUILD}/${S}.pid"

  /opt/SoapUI-5.5.0/bin/testrunner.sh \
    -Denv=${ENV} \
    -s "${TESTSUITE_PREFIX}${TESTSUITE}" \
    -j -f "${REPORT_DIR}/${TESTSUITE_PREFIX}${TESTSUITE}" \
    -r "${PROJECT}" \
  || true

  ssh omswrk1@${HOST} \
    "kill \$(cat ${OMS_BUILD}/${S}.pid)" \
  || true

  ssh omswrk1@${HOST} \
    "java -cp ${OMS_WORKSPACE} LogSearch ${OMS_BUILD}/${S}.log"
  
  ssh sewrk1@${HOST} \
    "kill \$(cat ${SE_BUILD}/${S}.pid)" \
  || true
    
  ssh sewrk1@${HOST} \
    "java -cp ${SE_WORKSPACE} LogSearch ${SE_BUILD}/${S}.log"
done

ERROR_DIR=${BUILD_DIR}/error_logs

mkdir -p "${ERROR_DIR}"
scp omswrk1@${HOST}:${OMS_BUILD}/*.err "${ERROR_DIR}"
scp sewrk1@${HOST}:${SE_BUILD}/*.err "${ERROR_DIR}"

ssh omswrk1@${HOST} "rm -r ${OMS_BUILD}" || true
ssh sewrk1@${HOST} "rm -r ${SE_BUILD}" || true

java -cp "java/local/target/classes:java/local/target/dependency/*" \
  com.amdocs.sanity.SanityRunner \
  --config config/sanity.properties \
  --buildDir ${BUILD_DIR} \
  --jobName "${JOB_NAME}_#${BUILD_NUMBER}" \
  --type "${SANITY_TYPE}" \
  --env ${ENV} \
  --tester "${TESTER}" \
  --project OE \
  --dmp x.x.x.x
