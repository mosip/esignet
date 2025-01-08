## eSignet with plugins docker

This directory contains files required to build the eSignet docker with default plugins preloaded. All the plugins 
available under [esignet-plugins](https://github.com/mosip/esignet-plugins) repository is included in the "esignet-with-plugins" docker image.

Based on the configured plugin name during the runtime, corresponding plugin jar will be copied to the eSignet 
classpath from the plugins directory in the docker container.
For example, "plugin_name_env" environment variable is set to "esignet-mock-plugin.jar", then "esignet-mock-plugin.jar" is copied 
to loader_path in the eSignet container. After successful copy eSignet service is started.

"esignet-with-plugins" docker image is created with "esignet" base image. The base image can also be directly used to start the eSignet 
service. There are 2 ways to use "esignet" base image directly.

1. Pass URL to download the plugin zip in the "plugin_url_env" environment variable of the container.
2. Mount external directory with the plugin onto "/home/mosip/plugins" directory in the container.

Either of the above 2 steps should be followed, and finally set "plugin_name_env" environment variable. With this setup, eSignet
service should get started with the configured plugin.

## License
This project is licensed under the terms of [Mozilla Public License 2.0](../LICENSE).
