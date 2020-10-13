package main

import (
	"encoding/json"
	"fmt"
	"io/ioutil"
	"os"
	"os/exec"
	"time"
)

func main() {
	if len(os.Args) < 2 {
		fmt.Println("Need version")
		os.Exit(1)
	}
	release_version := os.Args[1]
	isTest := false
	if len(os.Args) > 2 {
		isTest = true
	}

	fmt.Println("\nMake:", release_version, "\n")
	cmd := exec.Command("./build.sh")
	cmd.Stderr = os.Stderr
	cmd.Stdout = os.Stdout
	if err := cmd.Run(); err != nil {
		fmt.Println("Error compile", err)
		os.Exit(1)
	}

	fmt.Println("\nMake json")
	js := Release{}
	js.Name = "TorrServe"
	js.Version = release_version
	js.BuildDate = time.Now().Format("02.01.2006")
	//https://github.com/YouROK/TorrServe/releases/download/1.1.66/TorrServe_1.1.66.apk
	
	js.Link = "https://github.com/YouROK/TorrServe/releases/download/" + release_version + "/" + js.Name+"_"+release_version+".apk"
		
	buf, err := json.MarshalIndent(&js, "", " ")
	if err != nil {
		fmt.Println("Error make json")
		os.Exit(1)
	}
	if isTest {
		err = ioutil.WriteFile("test.json", buf, 0666)
	} else {
		err = ioutil.WriteFile("release.json", buf, 0666)
	}
	if err != nil {
		fmt.Println("Error write to json file:", err)
		os.Exit(1)
	}
	fmt.Println("\n\nEnter tag manually:\n")
	fmt.Println("git push origin", release_version)
	fmt.Println()
}

type Release struct {
	Name      string
	Version   string
	VersionCode string
	BuildDate string
	Link     string
}

