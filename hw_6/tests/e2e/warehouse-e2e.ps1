param(
    [string]$ProducerUrl = "http://localhost:8080",
    [string]$CassandraContainer = "hw6-cassandra"
)

$ErrorActionPreference = "Stop"

function Invoke-JsonPost($Path, $Body) {
    Invoke-RestMethod `
        -Method Post `
        -Uri "$ProducerUrl$Path" `
        -ContentType "application/json" `
        -Body ($Body | ConvertTo-Json -Depth 8)
}

$eventId = "hw7-e2e-received-1"
$productId = "SKU-HW7-E2E"
$zoneId = "ZONE-HW7"

Invoke-JsonPost "/api/events" @{
    event_id = $eventId
    event_type = "PRODUCT_RECEIVED"
    product_id = $productId
    zone_id = $zoneId
    quantity = 42
}

Start-Sleep -Seconds 5

$query = "SELECT available_quantity FROM warehouse.inventory_by_product_zone WHERE product_id='$productId' AND zone_id='$zoneId';"
$result = docker exec $CassandraContainer cqlsh -e $query

if ($LASTEXITCODE -ne 0) {
    throw "cqlsh failed"
}

if ($result -notmatch "\b42\b") {
    throw "Expected available_quantity=42 for $productId in $zoneId. Actual output: $result"
}

Write-Host "E2E scenario passed: PRODUCT_RECEIVED reached Cassandra"
