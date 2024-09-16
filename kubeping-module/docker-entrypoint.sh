#!/bin/bash -e

EXTRA_SCRIPT="$1"

exit_with_error() {
    if [ "$ERROR" != "0" ]; then
        echo "${red}[$(date '+%Y-%m-%d %H:%M:%S')] ERROR : $@ ${reset}";
        exit 1;
    fi
}

warn() {
    echo "${yellow}[$(date '+%Y-%m-%d %H:%M:%S')] WARN : $@ ${reset}";
}

info() {
    echo "${green}[$(date '+%Y-%m-%d %H:%M:%S')] INFO : $@ ${reset}";
}


# Meveo parameters
export MEVEO_DB_HOST=${MEVEO_DB_HOST:-postgres}
export MEVEO_DB_PORT=${MEVEO_DB_PORT:-5432}
export MEVEO_DB_NAME=${MEVEO_DB_NAME:-meveo}
export MEVEO_DB_USERNAME=${MEVEO_DB_USERNAME:-meveo}
export MEVEO_DB_PASSWORD=${MEVEO_DB_PASSWORD:-meveo}

# Wildfly parameters
export WILDFLY_BIND_ADDR=${WILDFLY_BIND_ADDR:-0.0.0.0}
export WILDFLY_MANAGEMENT_BIND_ADDR=${WILDFLY_MANAGEMENT_BIND_ADDR:-0.0.0.0}
export WILDFLY_PROXY_ADDRESS_FORWARDING=${WILDFLY_PROXY_ADDRESS_FORWARDING:-false}
export WILDFLY_LOG_CONSOLE_LEVEL=${WILDFLY_LOG_CONSOLE_LEVEL:-OFF}
export WILDFLY_LOG_FILE_LEVEL=${WILDFLY_LOG_FILE_LEVEL:-INFO}
export WILDFLY_LOG_MEVEO_LEVEL=${WILDFLY_LOG_MEVEO_LEVEL:-INFO}

# Debug parameters
export WILDFLY_DEBUG_ENABLE=${WILDFLY_DEBUG_ENABLE:-false}
export WILDFLY_DEBUG_PORT=${WILDFLY_DEBUG_PORT:-9999}
if [ "${WILDFLY_DEBUG_ENABLE}" = true ]; then
    export WILDFLY_LOG_CONSOLE_LEVEL=DEBUG
    export WILDFLY_LOG_FILE_LEVEL=DEBUG
    export WILDFLY_LOG_MEVEO_LEVEL=DEBUG
fi

DOCKER_GATEWAY_HOST=$(ip route|awk '/default/ { print $3 }')

# Keycloak parameters
if [ "x${KEYCLOAK_URL}" = "x" ]; then
    if ping -c 1 host.docker.internal &> /dev/null
    then
        export KEYCLOAK_URL="http://host.docker.internal:8081/auth"   # For Windows & MacOS localhost system
    else
        export KEYCLOAK_URL="http://${DOCKER_GATEWAY_HOST}:8081/auth" # For Linux system
    fi
else
    domain=$(echo ${KEYCLOAK_URL} | cut -d'/' -f3 | cut -d':' -f1)
    if [ "$domain" = "localhost" ]; then
        # Replace the address 'localhost' by docker gateway address
        KEYCLOAK_URL=$(echo "${KEYCLOAK_URL/localhost/$DOCKER_GATEWAY_HOST}")
    fi
    export KEYCLOAK_URL=${KEYCLOAK_URL}
fi
# export KEYCLOAK_URL=${KEYCLOAK_URL:-http://localhost:8080/auth}
export KEYCLOAK_REALM=${KEYCLOAK_REALM:-meveo}
export KEYCLOAK_CLIENT=${KEYCLOAK_CLIENT:-meveo-web}
export KEYCLOAK_SECRET=${KEYCLOAK_SECRET:-afe07e5a-68cb-4fb0-8b75-5b6053b07dc3}


# Read and execute a script for the extra configurations
if [ "x${EXTRA_SCRIPT}" != "x" ]; then
	info "Run the extra script: ${EXTRA_SCRIPT}"
	source ${EXTRA_SCRIPT}
fi


# wait with timeout 30s until postgres is up
timeout=30
counter=0
until pg_isready -h ${MEVEO_DB_HOST} -p ${MEVEO_DB_PORT}
do
    if [ $counter -gt $timeout ]; then
        ERROR=1; exit_with_error "Timeout occurred after waiting $timeout seconds for postgres"
    else
        info "Waiting for postgres (${MEVEO_DB_HOST}:${MEVEO_DB_PORT})..."
        counter=$((counter+1))
        sleep 1
    fi
done
info "Postgres is up"

if [ -z ${UPDATE_LIQUIBASE+x} ] || [ "${UPDATE_LIQUIBASE}" != false ]; then
    # Liquibase update the database for meveo app
    DB_CHANGELOG_FILE="/opt/jboss/liquibase/db_resources/changelog/db.rebuild.xml"
    if [ -f "${DB_CHANGELOG_FILE}" ]; then
        info "Update meveo database using liquibase"
        /opt/jboss/liquibase/liquibase \
            --url="jdbc:postgresql://${MEVEO_DB_HOST}:${MEVEO_DB_PORT}/${MEVEO_DB_NAME}" \
            --username=${MEVEO_DB_USERNAME} --password=${MEVEO_DB_PASSWORD} \
            --changeLogFile=${DB_CHANGELOG_FILE} \
            update \
            -Ddb.schema=public
    fi

    # Liquibase update the database for non-meveo app
    DB_CHANGELOG_OTHER="/opt/jboss/liquibase/db_resources/other-changelog"
    if [ -d "${DB_CHANGELOG_OTHER}" ]; then
        for changelog_dir in $(find $DB_CHANGELOG_OTHER -maxdepth 1 -type d | awk '{if(NR>1)print}')
        do
            changelog_file="$changelog_dir/db.xml"
            if [ -f "${changelog_file}" ]; then
                echo "Update $(basename $changelog_dir) database using liquibase"
                /opt/jboss/liquibase/liquibase \
                    --url="jdbc:postgresql://${MEVEO_DB_HOST}:${MEVEO_DB_PORT}/${MEVEO_DB_NAME}" \
                    --username=${MEVEO_DB_USERNAME} --password=${MEVEO_DB_PASSWORD} \
                    --changeLogFile=${changelog_file} \
                    update \
                    -Ddb.schema=public
            fi
        done
    fi

fi


# Reset standalone-full.xml file
#if [ -f ${JBOSS_HOME}/standalone/configuration/standalone-full.xml.org ]; then
    #cp -rf ${JBOSS_HOME}/standalone/configuration/standalone-full.xml.org ${JBOSS_HOME}/standalone/configuration/standalone-full.xml
#else
    #ERROR=1; exit_with_error "No default configuration file : ${JBOSS_HOME}/standalone/configuration/standalone-full.xml.org"
#fi

# Configure wildfly HA
#if [ -f ${JBOSS_HOME}/props/config-server.sh ]; then
    #info "Setting up HA for wildfly"
    #${JBOSS_HOME}/bin/jboss-cli.sh --file=${JBOSS_HOME}/props/config-server.cli
#fi

# Configure standalone-full-ha.xml
if [ -f ${JBOSS_HOME}/cli/standalone-full-ha-configuration.cli ]; then
    info "Configure standalone-full-ha.xml with ${JBOSS_HOME}/cli/standalone-full-ha-configuration.cli"
    ${JBOSS_HOME}/bin/jboss-cli.sh --file=${JBOSS_HOME}/cli/standalone-full-ha-configuration.cli
fi

# Disable logging
if [ "$DISABLE_LOGGING" = true ]; then
    if [ -f ${JBOSS_HOME}/cli/disable-logging.cli ]; then
        info "Disable the logging of wildfly with ${JBOSS_HOME}/cli/disable-logging.cli"
        ${JBOSS_HOME}/bin/jboss-cli.sh --file=${JBOSS_HOME}/cli/disable-logging.cli
    fi
fi

# Run the extra initial scripts:
# This code should be launched before run the extra CLI and properties,
# because it might need to run some scripts that export the environment variables.
if [ -d /docker-entrypoint-initdb.d ]; then
    for f in /docker-entrypoint-initdb.d/*.sh; do
        echo "Run the extra cli shell ${f}"
        [ -f "$f" ] && . "$f"
    done
fi

# Run the extra cli
if [ -d /docker-entrypoint-initdb.d ]; then
    for f in /docker-entrypoint-initdb.d/*.cli; do
        echo "Run the extra cli ${f}"
        [ -f "$f" ] && ${JBOSS_HOME}/bin/jboss-cli.sh --file=$f
    done
fi

# Configure meveo-admin.properties
## Store meveo-admin.properties file into the meveodata folder for keeping permanently.
## It's because the meveodata folder is volume-mapped folder.
if [ ! -f ${JBOSS_HOME}/meveodata/meveo-admin.properties ]; then
    MEVEO_ADMIN_BASE_URL=${MEVEO_ADMIN_BASE_URL:-http://localhost:8080/}
    MEVEO_ADMIN_WEB_CONTEXT=${MEVEO_ADMIN_WEB_CONTEXT:-meveo}

    TMP_PROPS_INPUT="/tmp/.tmp.${RANDOM}.props"

    echo "meveo.admin.baseUrl=${MEVEO_ADMIN_BASE_URL//:/\\:}" > ${TMP_PROPS_INPUT}
    echo "meveo.admin.webContext=${MEVEO_ADMIN_WEB_CONTEXT}" >> ${TMP_PROPS_INPUT}
    echo "" >> ${TMP_PROPS_INPUT}

    # Add the extra properties files
    if [ -d /docker-entrypoint-initdb.d ]; then
        for props_file in /docker-entrypoint-initdb.d/*.properties; do
            echo "Add the extra properties from ${props_file}"
            [ -f "${props_file}" ] && cat ${props_file} >> ${TMP_PROPS_INPUT}
            ## Insert a line break for each file
            echo "" >> ${TMP_PROPS_INPUT}
        done
    fi

    # Generate the final proerties file.
    if [ ! -x "${JBOSS_HOME}/props/properties-merger.sh" ]; then
        chmod +x "${JBOSS_HOME}/props/properties-merger.sh"
    fi
    ${JBOSS_HOME}/props/properties-merger.sh \
        -s ${JBOSS_HOME}/props/meveo-admin.properties \
        -i ${TMP_PROPS_INPUT} \
        -o ${JBOSS_HOME}/meveodata/meveo-admin.properties

    rm -f ${TMP_PROPS_INPUT}
fi
## Create a link of meveo-admin.properties into the wildfly configuration folder.
if [ ! -f ${JBOSS_HOME}/standalone/configuration/meveo-admin.properties ]; then
    ln -s ${JBOSS_HOME}/meveodata/meveo-admin.properties ${JBOSS_HOME}/standalone/configuration/meveo-admin.properties
fi

# Configure meveo-security.properties
## Store meveo-security.properties file into the meveodata folder for keeping permanently.
## It's because the meveodata folder is volume-mapped folder.
if [ ! -f ${JBOSS_HOME}/meveodata/meveo-security.properties ]; then
    ## Generate a random string
    random_string=$(cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 12 | head -n 1)
    ## Encrypt above random string using AES-256
    enc_string=$(echo ${random_string} | openssl enc -aes-256-cbc -a -k secret 2>/dev/null)
    ## Create meveo-security.properties file
    echo "meveo.security.secret="${enc_string} > ${JBOSS_HOME}/meveodata/meveo-security.properties
fi

## Create a link of meveo-security.properties into the wildfly configuration folder.
if [ ! -f ${JBOSS_HOME}/standalone/configuration/meveo-security.properties ]; then
    ln -s ${JBOSS_HOME}/meveodata/meveo-security.properties ${JBOSS_HOME}/standalone/configuration/meveo-security.properties
fi

system_memory_in_mb=`free -m | awk '/:/ {print $2;exit}'`
system_cpu_cores=`egrep -c 'processor([[:space:]]+):.*' /proc/cpuinfo`

# Check whether container has enough memory
if [ ${system_memory_in_mb} -lt 1500 ]; then
    ERROR=1; exit_with_error "The container doesn't have enough memory. Needs at least 2 GB of memory. Currently the available memory is ${system_memory_in_mb} MB."
fi

if [ "${system_cpu_cores}" -lt "1" ]; then
    system_cpu_cores="1"
fi


if [ "x${JBOSS_MODULES_SYSTEM_PKGS}" = "x" ]; then
   JBOSS_MODULES_SYSTEM_PKGS="org.jboss.byteman"
fi

#
# Specify options to pass to the Java VM.
#
if [ "x${JAVA_OPTS}" = "x" ]; then
    if [ "x${WILDFLY_CUSTOM_XMS}" = "x" ]; then
        WILDFLY_CUSTOM_XMS="1024m"
    fi
    if [ "x${WILDFLY_CUSTOM_XMX}" = "x" ]; then
        WILDFLY_CUSTOM_XMX="2048m"
    fi
    if [ "x${WILDFLY_CUSTOM_XMMS}" = "x" ]; then
        WILDFLY_CUSTOM_XMMS="96m"
    fi
    if [ "x${WILDFLY_CUSTOM_XMMX}" = "x" ]; then
        WILDFLY_CUSTOM_XMMX="512m"
    fi
    JAVA_OPTS="-Xms${WILDFLY_CUSTOM_XMS} -Xmx${WILDFLY_CUSTOM_XMX}"
    JAVA_OPTS="${JAVA_OPTS} -XX:MetaspaceSize=${WILDFLY_CUSTOM_XMMS} -XX:MaxMetaspaceSize=${WILDFLY_CUSTOM_XMMX}"
    JAVA_OPTS="${JAVA_OPTS} -XX:ParallelGCThreads=${system_cpu_cores} -XshowSettings:vm"
    JAVA_OPTS="${JAVA_OPTS} -Djava.net.preferIPv4Stack=true -Djboss.modules.system.pkgs=${JBOSS_MODULES_SYSTEM_PKGS} -Djava.awt.headless=true"
    JAVA_OPTS="${JAVA_OPTS} --add-opens java.base/jdk.internal.loader=ALL-UNNAMED --add-exports=java.base/jdk.internal.loader=ALL-UNNAMED"
else
    info "JAVA_OPTS already set in environment; overriding default settings with values: ${JAVA_OPTS}"
fi

# Glowroot - helps you get to the root of application performance issues.
if [ "${GLOWROOT_ENABLE}" = true ]; then
    JAVA_OPTS="${JAVA_OPTS} -javaagent:${JBOSS_HOME}/glowroot/glowroot.jar"

    if [ -f "${JBOSS_HOME}/glowroot/admin.json" ]; then
        ## Change the bind address for the access from remote machines.
        sed -i 's,"bindAddress": "127.0.0.1","bindAddress": "0.0.0.0",g' ${JBOSS_HOME}/glowroot/admin.json
    else
        cat > ${JBOSS_HOME}/glowroot/admin.json << EOL
{
  "web": {
    "bindAddress": "0.0.0.0"
  }
}
EOL
    fi
fi

#
# The extra options to pass to the Java VM.
#
if [ "x${JAVA_EXTRA_OPTS}" != "x" ]; then
    JAVA_OPTS="${JAVA_OPTS} ${JAVA_EXTRA_OPTS}"
fi

export JAVA_OPTS=$JAVA_OPTS

# Set admin user/pass for wildfly
if [ ! -z "${WILDFLY_ADMIN_USER}" -a ! -z "${WILDFLY_ADMIN_PASSWORD}" ]; then
    if [ -f ${JBOSS_HOME}/bin/add-user.sh ]; then
        info "Add an wildfly user : ${WILDFLY_ADMIN_USER}"
        ${JBOSS_HOME}/bin/add-user.sh -u ${WILDFLY_ADMIN_USER} -p ${WILDFLY_ADMIN_PASSWORD} --silent
    fi
fi

# Set admin user/pass for keycloak
if [ ! -z "${KEYCLOAK_ADMIN_USER}" -a ! -z "${KEYCLOAK_ADMIN_PASSWORD}" ]; then
    if [ -f ${JBOSS_HOME}/bin/add-user-keycloak.sh ]; then
        info "Add a keycloak user : ${KEYCLOAK_ADMIN_USER}"
        ${JBOSS_HOME}/bin/add-user-keycloak.sh --user ${KEYCLOAK_ADMIN_USER} --password ${KEYCLOAK_ADMIN_PASSWORD}
    fi
fi

WILDFLY_OPTS="${WILDFLY_OPTS} -b ${WILDFLY_BIND_ADDR} -bmanagement ${WILDFLY_MANAGEMENT_BIND_ADDR}"
if [ "${WILDFLY_DEBUG_ENABLE}" = true ]; then
    #allow to mount /opt/jboss/wildflyForHost and use it to create server adapter in IDE
    #cp -r ${JBOSS_HOME}/{jboss-modules.jar,bin,cli,standalone,domain,modules}  /opt/jboss/wildflyForHost
    WILDFLY_OPTS="${WILDFLY_OPTS} --debug *:${WILDFLY_DEBUG_PORT}"
fi

info "Starting Wildfly"
exec ${JBOSS_HOME}/bin/standalone.sh ${WILDFLY_OPTS} -c standalone-full-ha.xml -b $MY_POD_IP -bprivate $MY_POD_IP -bmanagement 0.0.0.0 -Djboss.node.name=$HOSTNAME -Djboss.txn.uniqueid=$MY_POD_IP -Djboss.messaging.cluster.password=acme

exit 0
