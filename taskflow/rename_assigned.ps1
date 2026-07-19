$dir = "C:\Users\SEC\OneDrive\Desktop\Project\Ryokai\Ryokai_backend\taskflow\src\main\java\com\example\taskflow"
Get-ChildItem -Path $dir -Recurse -Filter "*.java" | ForEach-Object {
    $content = Get-Content $_.FullName -Raw
    $newContent = $content -replace 'TaskStatus\.ASSIGNED', 'TaskStatus.IN_PROGRESS'
    $newContent = $newContent -replace '\bASSIGNED,', 'IN_PROGRESS,'
    if ($_.Name -in 'TaskWorkflowService.java', 'OrganizationService.java') {
        $newContent = $newContent -replace '"ASSIGNED"', '"IN_PROGRESS"'
    }
    $newContent = $newContent -replace 'TODO - ASSIGNED', 'TODO - IN_PROGRESS'
    $newContent = $newContent -replace '// ASSIGNED', '// IN_PROGRESS'
    $newContent = $newContent -replace 'TODO \(unclaimed\) - ASSIGNED \(claimed\)', 'TODO (unclaimed) - IN_PROGRESS (claimed)'
    if ($content -cne $newContent) {
        Set-Content -Path $_.FullName -Value $newContent -NoNewline
        Write-Host "Updated $($_.Name)"
    }
}
