const getBooleanValue = (inputValue) => {
    if (typeof(inputValue) === 'undefined' || inputValue === null || inputValue === '') {
        console.log("no input provided")
        return false;
    }
    return /^true$/i.test(inputValue);
}

export { getBooleanValue };