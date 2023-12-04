import axios from "axios";

const configEndpoint = "/config.json";

const configService = async (value) => {
  const response = await axios.get(configEndpoint);
  return response.data[value];
};

export default configService;