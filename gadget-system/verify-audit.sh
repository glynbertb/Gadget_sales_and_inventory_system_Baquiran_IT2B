#!/usr/bin/env bash
# Run verification against disposable databases; never opens the application's Gadget.db.
set -euo pipefail
project_dir="$(cd "$(dirname "$0")" && pwd)"
verification_dir="$(mktemp -d "${TMPDIR:-/tmp}/gadget-audit-verification.XXXXXX")"
java_command="${JAVA_HOME:+$JAVA_HOME/bin/}java"
javac_command="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
dependency_jar="$project_dir/target/gadget-sales-inventory-1.0.0.jar"
mkdir -p "$verification_dir/classes"
while IFS= read -r -d '' source_file; do
    printf '"%s"\n' "$source_file"
done < <(find "$project_dir/src/main/java" "$project_dir/src/test/java" -name '*.java' -print0) > "$verification_dir/sources.args"
"$javac_command" --release 17 -cp "$dependency_jar" -d "$verification_dir/classes" "@$verification_dir/sources.args"
for scenario in fresh legacy previous orphan; do
    "$java_command" "-Dgadget.db=$verification_dir/$scenario.db" \
        -cp "$verification_dir/classes:$dependency_jar" AuditLogVerification "$scenario"
done
"$java_command" "-Dgadget.db=$verification_dir/cash.db" \
    -cp "$verification_dir/classes:$dependency_jar" com.gadget_sale_inventory_system.ui.CashCheckoutVerification
printf '\nVerification databases: %s\n' "$verification_dir"
