#!/bin/sh
STARTTIME=$SECONDS
TIMESTEPS="1000"
SEEDS=30
SCENARIO="hist3_broadcast"
SCENARIO_FILE="scenarios/scenario3_hist_unweighted.xml"

# Only set if not already set
[ -z $LOGDIR ] && LOGDIR="./logs/current"

PREFIX="$LOGDIR/$SCENARIO-$TIMESTEPS"
EXTRA_ARGS=$*

if [ ! -d "$LOGDIR" ]; then
    echo "Error: $LOGDIR does not exist"
    exit
fi

if [ ! -f "$SCENARIO_FILE" ]; then
    echo "Error: $SCENARIO_FILE does not exist"
    exit
fi

# Iterate over numbers, also use as seeds
for SEED in `seq 1 $SEEDS`
do
    OUTPUT_FILE="$PREFIX-$SEED.csv"
    SUMMARY_FILE="$PREFIX-$SEED-summary.txt"
    STDOUT_FILE="/dev/null" #"$PREFIX-$SEED-stdout.txt"
    ARGS="$SCENARIO_FILE --no-gui -t $TIMESTEPS -o $OUTPUT_FILE --summaryfile $SUMMARY_FILE --seed $SEED"
    echo "Running with args: $ARGS $EXTRA_ARGS"

    ./run.sh $ARGS" "$EXTRA_ARGS > $STDOUT_FILE
done

echo "$SEEDS seeds done in "$(($SECONDS-$STARTTIME))" seconds"