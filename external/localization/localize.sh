#!/bin/bash
set -euo pipefail

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

class_directory=$1
test_class_directory=$2
dependencies=$3
gzoltar_data_dir=$4

package=""
gzoltar_cli_jar=$SCRIPT_DIR/gzoltarcli.jar
gzoltar_agent_jar=$SCRIPT_DIR/gzoltaragent.jar

mkdir -p $gzoltar_data_dir

gzoltar_ser=gzoltar.ser
test_methods_file=$gzoltar_data_dir/test_methods.txt

java -cp "$class_directory:$test_class_directory:$dependencies:$gzoltar_cli_jar" com.gzoltar.cli.Main listTestMethods $test_class_directory/$package --outputFile $test_methods_file
java -javaagent:$gzoltar_agent_jar=destfile=$gzoltar_ser,buildlocation=$class_directory,inclnolocationclasses=false,output="FILE" -cp "$class_directory:$test_class_directory:$dependencies:$gzoltar_cli_jar" com.gzoltar.cli.Main runTestMethods --testMethods $test_methods_file --collectCoverage
java -cp "$class_directory:$test_class_directory:$dependencies:$gzoltar_cli_jar" com.gzoltar.cli.Main faultLocalizationReport --buildLocation $class_directory --granularity line --inclPublicMethods --inclStaticConstructors --inclDeprecatedMethods --dataFile $gzoltar_ser --outputDirectory $gzoltar_data_dir --family "sfl" --formula "ochiai" --metric "entropy" --formatter "txt"


cp $gzoltar_data_dir/sfl/txt/tests.csv $gzoltar_data_dir/tests
python3 <(cat << EOF
import sys
first_line = True
for line in sys.stdin:
    if first_line:
        print(line, end="")
        first_line = False
        continue
    tmp, suspiciousness = line.split(";")
    method, lineno = tmp.split(":")
    classname = method.split("#")[0].replace("$", ".", 1)
    print(f"<{classname}{{#{lineno},{suspiciousness}", end="")
EOF
) < $gzoltar_data_dir/sfl/txt/ochiai.ranking.csv > $gzoltar_data_dir/spectra
exit

# java -cp "target/classes:target/test-classes:target/dependency/*:/home/crhf/arja/gzoltar/lib/gzoltarcli.jar" com.gzoltar.cli.Main listTestMethods target/test-classes/org --outputFile test_methods.txt
# java -javaagent:/home/crhf/arja/gzoltar/lib/gzoltaragent.jar=destfile=gzoltar.ser,buildlocation=target/classes,inclnolocationclasses=false,output="FILE" -cp "target/classes:target/test-classes:target/dependency/*:/home/crhf/arja/gzoltar/lib/gzoltarcli.jar" com.gzoltar.cli.Main runTestMethods --testMethods test_methods.txt --collectCoverage
# java -cp "target/classes:target/test-classes:target/dependency/*:/home/crhf/arja/gzoltar/lib/gzoltarcli.jar" com.gzoltar.cli.Main faultLocalizationReport --buildLocation target/classes --granularity line --inclPublicMethods --inclStaticConstructors --inclDeprecatedMethods --dataFile gzoltar.ser --outputDirectory gzoltar_data --family "sfl" --formula "ochiai" --metric "entropy" --formatter "txt"
