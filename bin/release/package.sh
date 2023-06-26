#bin

bin=$(dirname "$0")/..
bin=$(cd "$bin">/dev/null || exit; pwd)
APP_HOME=$(cd "$bin"/..>/dev/null || exit; pwd)

EXOTIC_AMAZON_VERSION=$(basename "$APP_HOME")
cd "$APP_HOME"/.. || exit 0
echo "Working directory: "
pwd
echo "Directory to archive: "
echo "$EXOTIC_AMAZON_VERSION"

tar -czf "exotic-amazon-$EXOTIC_AMAZON_VERSION.tar.gz" "$EXOTIC_AMAZON_VERSION"
