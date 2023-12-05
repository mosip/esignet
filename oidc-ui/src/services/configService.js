import axios from "axios";

const configEndpoint = "/config.json";

const configService = async () => {
  const response = await axios.get(configEndpoint);
  return response.data;
};

export default configService;