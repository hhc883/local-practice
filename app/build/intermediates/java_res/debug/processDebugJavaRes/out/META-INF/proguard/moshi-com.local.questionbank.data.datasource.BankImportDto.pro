-if class com.local.questionbank.data.datasource.BankImportDto
-keepnames class com.local.questionbank.data.datasource.BankImportDto
-if class com.local.questionbank.data.datasource.BankImportDto
-keep class com.local.questionbank.data.datasource.BankImportDtoJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.local.questionbank.data.datasource.BankImportDto
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.local.questionbank.data.datasource.BankImportDto
-keepclassmembers class com.local.questionbank.data.datasource.BankImportDto {
    public synthetic <init>(java.lang.String,java.lang.String,java.util.List,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
