#bin

echo "Usage: deploy.sh [HOST] [USER]"

if [ $# -eq 1 ]; then
  # Change to your own target host
  TARGET_HOST=$1
  shift
fi

if [ $# -eq 1 ]; then
  # Change to your own target host
  REMOTE_USER=$1
  shift
else
  # Change to your own username
  REMOTE_USER=$USER
fi

bin=$(dirname "$0")/..
bin=$(cd "$bin">/dev/null || exit; pwd)
APP_HOME=$(cd "$bin"/..>/dev/null || exit; pwd)

echo "Deploy the project ..."

SNAPSHOT_VERSION=$(head -n 1 "$APP_HOME/VERSION")
VERSION="$SNAPSHOT_VERSION"

#echo "Changing version ..."
#VERSION=${SNAPSHOT_VERSION//"-SNAPSHOT"/""}
#echo "$VERSION" > "$APP_HOME"/VERSION
#find "$APP_HOME" -name 'pom.xml' -exec sed -i "s/$SNAPSHOT_VERSION/$VERSION/" {} \;

#mvn clean
#mvn package

######################################################
# Deploy
######################################################

PACK_DIR="$APP_HOME/target/exotic-amazon-$VERSION"
if [ -e "$PACK_DIR" ]; then
  rm -r "$PACK_DIR"
fi

mkdir -p "$PACK_DIR"
mkdir "$PACK_DIR"/bin
mkdir "$PACK_DIR"/conf

JAR="$APP_HOME/target/exotic-amazon-$VERSION.jar"
if [ ! -e "$JAR" ]; then
  echo "$JAR does not exist"
  exit 1
fi

cp "$JAR" "$PACK_DIR/"
cp VERSION "$PACK_DIR/"
cp -r "$APP_HOME"/bin "$PACK_DIR/"
cp src/main/resources/config/* "$PACK_DIR/conf/"
cp src/main/resources/logback-prod.xml "$PACK_DIR/conf/"

ARCHIVE_FILE="exotic-amazon-$VERSION.tar.gz"
cd "$APP_HOME/target" || exit
echo "Make tarball in directory (should be target): "
pwd
tar -czf "$ARCHIVE_FILE" "exotic-amazon-$VERSION"
cd - || exit
echo "Return to application home: "
pwd

if [[ -z "$TARGET_HOST" ]]; then
  echo "Target host not specified"
  exit 0
fi

if [[ -z "$REMOTE_USER" ]]; then
  echo "Remote user not specified"
  exit 0
fi

DESTINATION_BASE_DIR="/home/$REMOTE_USER/wwwpub/pub/exotic/exotic-amazon"
DESTINATION_DIR="$DESTINATION_BASE_DIR/$VERSION"
DESTINATION="$REMOTE_USER@$TARGET_HOST:$DESTINATION_DIR/"

rsync --update -raz --progress "$APP_HOME/target/$ARCHIVE_FILE" "$DESTINATION"

echo "Finished at" "$(date)"

open "http://platonic.fun/pub/exotic/exotic-amazon" > /dev/null 2>&1 &
