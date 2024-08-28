#
# COPYRIGHT Ericsson 2021
#
#
#
# The copyright to the computer program(s) herein is the property of
#
# Ericsson Inc. The programs may be used and/or copied only with written
#
# permission from Ericsson Inc. or in accordance with the terms and
#
# conditions stipulated in the agreement/contract under which the
#
# program(s) have been supplied.
#

# default login is demo/demo
helm upgrade --install sftp-server emberstack/sftp --set service.port=9023 --set image.pullPolicy=IfNotPresent --set fullnameOverride=sftp-server --set imagePullSecrets[0].name=armdocker

helm upgrade --install dmm https://arm.seli.gic.ericsson.se/artifactory/proj-eric-oss-drop-helm-local/eric-oss-dmm/eric-oss-dmm-0.0.0-44.tgz --set global.registry.username=admin --set global.registry.password=ericsson --set tags.data-catalog=true --set tags.dmaap=false --wait --set global.pullSecret=armdocker

kubectl patch service eric-oss-dmm-data-message-bus-kf-client --type='json' -p '[{"op":"replace","path":"/spec/type","value":"NodePort"}]'
kubectl patch service sftp-server --type='json' -p '[{"op":"replace","path":"/spec/type","value":"NodePort"}]'
