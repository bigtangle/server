
 
./gradlew clean build
./gradlew buildDockerImage
docker-compose down

docker-compose up -d

#docker start as-vdv-aus_processor
#docker logs as-vdv-aus_processor
