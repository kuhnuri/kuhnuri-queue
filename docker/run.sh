#!/usr/bin/env bash

CLASSPATH=conf/
for i in lib/*.jar; do
    CLASSPATH=$CLASSPATH:$i
done
PLAY_OPTS="-Duser.dir=/opt/app"
if [ ! -z "$ENVIRONMENT" ]; then
    PLAY_OPTS="$PLAY_OPTS -Dconfig.file=conf/$ENVIRONMENT.conf"
fi
if [ -f /opt/app/RUNNING_PID ]; then
    rm /opt/app/RUNNING_PID
fi
exec java $JAVA_OPTS $PLAY_OPTS -cp $CLASSPATH play.core.server.ProdServerStart
