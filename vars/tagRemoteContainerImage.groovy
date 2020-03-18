#!/usr/bin/env groovy

// Copy (retag) remote container image and delete original one (if requested)
def call(Map params) {

    final String credentialsId = params.credentialsId
    final String sourceImage = params.sourceImage
    final String targetImage = params.targetImage
    final String bearerToken = params.bearerToken ?: ''

    final Boolean deleteOriginalImage = params.deleteOriginalImage ?: false


    withCredentials([usernameColonPassword(credentialsId: "${credentialsId}", variable: 'REGISTRY_CREDENTIALS')]) {
        retry(3) {
            sh """
                skopeo copy \
                    --src-creds ${env.REGISTRY_CREDENTIALS} \
                    --dest-creds ${env.REGISTRY_CREDENTIALS} \
                    docker://${sourceImage} \
                    docker://${targetImage}
            """
        }

        if (deleteOriginalImage) {
            
            final String[] imageNameParts = sourceImage.split("/|:")
            final Map image = [
                registry: imageNameParts[0],
                org: imageNameParts[1],
                repo: imageNameParts[2],
                tag: imageNameParts[3]
            ]
            retry(3) {
                if (image.registry == "quay.io") {
                    withCredentials([string(credentialsId: "${bearerToken}", variable: 'REGISTRY_TOKEN')]) {
                        final String responseCode = sh (
                            script: "curl -s -o /dev/null -w '%{http_code}' 'https://quay.io/api/v1/repository/${image.org}/${image.repo}/tag/${image.tag}' -X DELETE -H 'authorization: Bearer ${env.REGISTRY_TOKEN}'",
                            returnStdout: true
                        ).trim()
                        sh "[[ ${responseCode} = 204 ]] || sleep 10"
                    }
                } else {
                    sh """
                        skopeo delete \
                            --creds ${env.REGISTRY_CREDENTIALS} \
                            docker://${sourceImage} \
                        || sleep 10
                    """
                }
            }
        }
    }
}
