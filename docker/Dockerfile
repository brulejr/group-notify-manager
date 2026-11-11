# syntax=docker/dockerfile:1
#
# The group-notify-manager application image, used by docker/local and docker/prod.
#
# Gradle builds the jar first; this only packages it:
#   ./gradlew bootJar
#   docker build -f docker/Dockerfile -t group-notify-manager .
# The build context is the repository root. Docker reads docker/Dockerfile.dockerignore,
# which admits nothing but the jar.
#
# The jar is run whole rather than extracted into layers, as iot-pipeline's is: it starts
# with PropertiesLauncher, which adds the plugin jars in LOADER_PATH to the class path,
# and that is only documented for the packaged jar.

FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --no-create-home app \
    && mkdir -p /app/plugins && chown app:app /app/plugins
WORKDIR /app
COPY build/libs/group-notify-manager.jar app.jar
USER app
# Every setting comes from the environment; see docker/prod/README.md. The jar carries
# no application-local.yml, so the local profile would have nothing to offer here.
ENV PROFILE=prod
# Delivery plugins beyond the bundled ones: mount jars here; see docs/plugins.md.
ENV LOADER_PATH=/app/plugins
EXPOSE 5002
ENTRYPOINT ["java", "-jar", "app.jar"]
