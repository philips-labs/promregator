#!groovy

def executeShell(command) {
	def result = sh returnStdout: true, script: command
	return result.trim()
}

def getVersion() {
	// for idea, see also https://stackoverflow.com/questions/3545292/how-to-get-maven-project-version-to-the-bash-command-line
	def mvnOutput = executeShell """
		printf 'VERSION=\${project.version}\n0\n' | mvn org.apache.maven.plugins:maven-help-plugin:2.1.1:evaluate | egrep '^VERSION'
	"""
	return mvnOutput.substring(9) // trim prefix "VERSION="
}

timestamps {
	node("slave") {
		dir("build") {
			git url: 'https://github.com/promregator/promregator.git'
			
			stage("Build") {
				sh """#!/bin/bash -xe
				export CF_PASSWORD=dummypassword
				mvn -U -B clean verify
				"""
			}
			
			stage("Post-processing quality data") {
				junit 'target/surefire-reports/*.xml'
				
				step([
					$class: 'FindBugsPublisher',
					pattern: '**/findbugsXml.xml',
					failedTotalAll: '100'
				])
				
				step([
					$class: 'PmdPublisher',
					failedTotalAll: '100'
				])
				
				step([
					$class: 'JacocoPublisher'
				])
			}
			
			stage("Archive") {
				archiveArtifacts 'target/promregator*.jar'
			}
			
			stage("Create Docker Container") {
				def currentVersion = getVersion()
				println "Current version is ${currentVersion}"
				
				dir("docker") {
					sh "docker info"
					
					if (!currentVersion.contains("-SNAPSHOT")) {
						// docker push
					}
				}
			}
			
		}
		
	}
}