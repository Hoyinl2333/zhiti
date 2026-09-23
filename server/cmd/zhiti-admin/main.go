package main

import (
	"encoding/json"
	"flag"
	"fmt"
	"os"

	"com.xiaoyunduo.zhiti/server/internal/store"
)

func main() {
	if len(os.Args) < 2 {
		usage()
	}
	command := os.Args[1]
	flags := flag.NewFlagSet(command, flag.ExitOnError)
	statePath := flags.String("state", "data/state.json", "state file")
	pepper := flags.String("pepper", os.Getenv("ZHITI_PEPPER"), "server pepper")
	count := flags.Int("count", 1, "number of codes")
	code := flags.String("code", "", "activation code")
	flags.Parse(os.Args[2:])
	if len(*pepper) < 32 {
		fatal("set ZHITI_PEPPER or --pepper with at least 32 characters")
	}
	state, err := store.Open(*statePath, *pepper)
	if err != nil {
		fatal(err.Error())
	}
	switch command {
	case "create":
		codes, err := state.CreateCodes(*count)
		if err != nil {
			fatal(err.Error())
		}
		for _, value := range codes {
			fmt.Println(value)
		}
	case "revoke":
		if err := state.Revoke(*code); err != nil {
			fatal(err.Error())
		}
		fmt.Println("revoked")
	case "unbind":
		if err := state.Unbind(*code); err != nil {
			fatal(err.Error())
		}
		fmt.Println("unbound")
	case "status":
		records, err := state.Status()
		if err != nil {
			fatal(err.Error())
		}
		for index := range records {
			records[index].CodeHash = records[index].CodeHash[:12]
			if records[index].DeviceHash != "" {
				records[index].DeviceHash = records[index].DeviceHash[:12]
			}
			if records[index].TokenHash != "" {
				records[index].TokenHash = records[index].TokenHash[:12]
			}
		}
		json.NewEncoder(os.Stdout).Encode(records)
	default:
		usage()
	}
}

func usage() {
	fmt.Fprintln(os.Stderr, "usage: zhiti-admin create|revoke|unbind|status [flags]")
	os.Exit(2)
}

func fatal(message string) {
	fmt.Fprintln(os.Stderr, message)
	os.Exit(1)
}
