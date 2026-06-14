-if class com.local.questionbank.data.datasource.QuestionImportDto
-keepnames class com.local.questionbank.data.datasource.QuestionImportDto
-if class com.local.questionbank.data.datasource.QuestionImportDto
-keep class com.local.questionbank.data.datasource.QuestionImportDtoJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.local.questionbank.data.datasource.QuestionImportDto
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.local.questionbank.data.datasource.QuestionImportDto
-keepclassmembers class com.local.questionbank.data.datasource.QuestionImportDto {
    public synthetic <init>(java.lang.String,java.lang.String,java.util.List,java.util.List,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
