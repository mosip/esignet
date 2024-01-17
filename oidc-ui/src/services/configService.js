import axios from "axios";

import { CONFIG } from "../constants/routes";

const configService = async () => {
  const response = await axios.get(CONFIG);
  return response.data;
};

export default configService;