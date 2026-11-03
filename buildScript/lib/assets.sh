#!/bin/bash

set -e

DIR=app/src/main/assets/sing-box
rm -rf $DIR
mkdir -p $DIR
cd $DIR

# the latest release tag of a GitHub repository; with GITHUB_TOKEN set (CI) the call is authenticated, since
# the unauthenticated rate limit is shared by the runners' addresses. An empty answer stops the build here.
get_latest_release() {
  local tag
  tag=$(curl --silent --retry 3 ${GITHUB_TOKEN:+-H "Authorization: Bearer $GITHUB_TOKEN"} \
    "https://api.github.com/repos/$1/releases/latest" |
    grep '"tag_name":' |
    sed -E 's/.*"([^"]+)".*/\1/')
  if [ -z "$tag" ]; then
    echo "no latest release of $1 (GitHub API rate limit or no network?)" >&2
    exit 1
  fi
  echo "$tag"
}

####
VERSION_GEOIP=`get_latest_release "SagerNet/sing-geoip"`
echo VERSION_GEOIP=$VERSION_GEOIP
echo -n $VERSION_GEOIP > geoip.version.txt
curl -fLSsO https://github.com/SagerNet/sing-geoip/releases/download/$VERSION_GEOIP/geoip.db
xz -9 geoip.db

####
VERSION_GEOSITE=`get_latest_release "SagerNet/sing-geosite"`
echo VERSION_GEOSITE=$VERSION_GEOSITE
echo -n $VERSION_GEOSITE > geosite.version.txt
curl -fLSsO https://github.com/SagerNet/sing-geosite/releases/download/$VERSION_GEOSITE/geosite.db
xz -9 geosite.db
