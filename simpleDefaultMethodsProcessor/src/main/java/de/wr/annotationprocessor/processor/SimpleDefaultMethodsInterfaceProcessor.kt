package de.wr.annotationprocessor.processor

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.expr.LiteralStringValueExpr
import com.github.javaparser.ast.expr.StringLiteralExpr
import com.github.javaparser.ast.stmt.BlockStmt
import com.github.javaparser.ast.stmt.ReturnStmt
import de.wr.libsimpledefaultmethods.*
import io.reactivex.Observable
import io.reactivex.rxkotlin.toObservable
import java.io.BufferedWriter
import java.io.IOException

import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Filer
import javax.annotation.processing.Messager
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import javax.tools.Diagnostic

import java.util.*

import javax.lang.model.SourceVersion.latestSupported
import javax.lang.model.element.*
import kotlin.collections.HashSet

import com.github.javaparser.ast.Modifier as AstModifier

class SimpleDefaultMethodsInterfaceProcessor : AbstractProcessor() {

    private val methodsForClass = Hashtable<TypeElement, List<ExecutableElement>>()

    private lateinit var objectType: String
    private lateinit var typeUtils: Types
    private lateinit var elementUtils: Elements
    private lateinit var filer: Filer
    private lateinit var messager: Messager

    override fun getSupportedSourceVersion(): SourceVersion {
        return latestSupported()
    }

    override fun getSupportedAnnotationTypes() = supportedAnnotations

    @Synchronized override fun init(processingEnv: ProcessingEnvironment) {
        super.init(processingEnv)
        typeUtils = processingEnv.typeUtils
        elementUtils = processingEnv.elementUtils
        filer = processingEnv.filer
        messager = processingEnv.messager
    }

//    private fun addMethodToClass(clazz: TypeElement, method: ExecutableElement) {
//        val executableElements = methodsForClass[clazz]
//        if (executableElements != null) {
//            for (executableElement in executableElements) {
//                System.err.println(executableElement)
//            }
//        }
//    }

    override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {

        val methodPair = supportedAnnotationsClasses.toObservable()
                .flatMap {
                    roundEnv.getElementsAnnotatedWith(it).toObservable()
                }.flatMap { el ->
            when {
                el.kind == ElementKind.CLASS -> Observable.just(el)
                        .flatMap { it.enclosedElements.toObservable() }
                        .filter { it is ExecutableElement }
                        .map { Pair(el, it) }
                el.kind == ElementKind.PARAMETER -> when {
                    el.enclosingElement != null -> Observable.just(Pair(el, el.enclosingElement))
                    else -> Observable.empty()
                }.filter { it.second is ExecutableElement }
                else -> Observable.just(Pair(el, el))
            }
        }.blockingIterable()

        val clazzes = methodPair.groupBy { it.second }
                .entries
                .groupBy { it.key.enclosingElement }

        generateMethodsForClazz(clazzes)

        return true

//                .groupBy {
//                    it.key!!.enclosingElement
//                }
//                .flatMap(this::generateMethodsForClazz)
//                .reduce { r, acc -> r && acc }
//                .defaultIfEmpty(false)
//                .blockingGet()

//            .reduce { (r, acc) -> acc && r }
//            if (element.kind != ElementKind.METHOD) {
//                error(orig, "The annotated element %s is not meant for %s",
//                        element, orig)
//                return false
//            }

//            // We can cast it, because we know that it of ElementKind.CLASS
//            val methodElement = element as TypeElement
//
//            objectType = typeElement.simpleName.toString()
//
//            val qualifiedName = typeElement.qualifiedName.toString()
//
//            info(element, "The annotated element %s found", orig)
//
//            val classesList = typeElement.enclosedElements
//                    .filter { it -> it is ExecutableElement && it.returnType.toString().contains("Void") }
//                    .map { it -> it as ExecutableElement }
//
//            if (classesList.isNotEmpty()) {
//                classesList.forEach { it -> generateAutoValueClasses(element, it, typeElement) }
//                if (element.annotationMirrors
//                        .union(classesList.flatMap { it -> it.annotationMirrors })
//                        .any { it -> it.toString().contains("Gson") }) {
//                    generateAutoValueGsonFactory(typeElement)
//                }
//            }
//        }
    }

    private fun generateMethodsForClazz(clazzes: Map<Element, List<Map.Entry<Element, List<Pair<Element, Element>>>>>) {

        clazzes.forEach {

            val clazzElement = it.key as TypeElement

            info(clazzElement, "Class containing defaults found %s", clazzElement )

            try {
                val fileName = clazzElement.simpleName.toString() + "Defaults"
                val source = processingEnv.filer.createSourceFile(fileName)

                val writer = BufferedWriter(source.openWriter())

                val cu = CompilationUnit();
                // set the package
                cu.setPackageDeclaration(getPackageName(clazzElement));

                val interf = cu.addInterface(fileName, AstModifier.PUBLIC)

                it.value.forEach {

                    val method = it.key as ExecutableElement

                    info(method, "Class %s contains default method %s", clazzElement, method.toString() )

                    val returnStmt = ReturnStmt().setExpression(StringLiteralExpr("test"))

                    interf.addMethod(method.simpleName.toString(), AstModifier.DEFAULT).setBody(
                            BlockStmt().addStatement(returnStmt)
                    ).setType(method.returnType.toString())

                    it.value.forEach {

                        info(it.second, "Method %s contains default elements %s", it.second, it.first )
                    }
                }

                writer.run {
                    write(cu.toString())
                    flush()
                    close()
                }

                info(clazzElement, "Interface default generated: %s %n", fileName)
            } catch (e: IOException) {
                System.err.println(objectType + " :" + e + e.message)
            }
        }
    }

//    private fun generateAutoValueGsonFactory(typeElement: TypeElement) {
//        try {
//            val fileName = typeElement.simpleName.substring(0, 1).toUpperCase() + typeElement.simpleName.substring(1) + "TypeAdapterFactory"
//            val source = processingEnv.filer.createSourceFile(fileName)
//
//            val writer = BufferedWriter(source.openWriter())
//
//            val cu = CompilationUnit();
//            // set the package
//            cu.setPackageDeclaration(getPackageName(typeElement));
//
//            cu.addClass(fileName, AstModifier.PUBLIC, AstModifier.ABSTRACT)
//                .addAnnotation(GsonTypeAdapterFactory::class.java)
//                .addImplementedType(TypeAdapterFactory::class.java)
//                .addMethod("create", AstModifier.PUBLIC, AstModifier.STATIC).setBody(
//                    BlockStmt().addStatement(ReturnStmt()
//                            .setExpression(ObjectCreationExpr().setType("AutoValueGson_$fileName")))
//                ).setType(TypeAdapterFactory::class.java)
//
//            writer.run {
//                write(cu.toString())
//                flush()
//                close()
//            }
//
//            info(typeElement, "Gson Data class generated: %s %n", fileName)
//        } catch (e: IOException) {
//            System.err.println(objectType + " :" + e + e.message)
//        }
//    }
//
//    private fun generateAutoValueClasses(element: TypeElement, it: ExecutableElement, typeElement: TypeElement) {
//        info(element, "Data class def found: %s", it.simpleName)
//
//        try {
//            val fileName = it.simpleName.substring(0, 1).toUpperCase() + it.simpleName.substring(1)
//            val source = processingEnv.filer.createSourceFile(fileName)
//
//            val writer = BufferedWriter(source.openWriter())
//
//            generateDataClass(
//                    element,
//                    writer,
//                    fileName,
//                    getPackageName(typeElement),
//                    it)
//
//            writer.run {
//                flush()
//                close()
//            }
//
//            info(element, "Data class generated: %s %n", fileName)
//        } catch (e: IOException) {
//            System.err.println(objectType + " :" + e + e.message)
//        }
//    }
//
    private fun getPackageName(typeElement: TypeElement) =
            typeElement.qualifiedName.substring(0, typeElement.qualifiedName.length - typeElement.simpleName.length - 1)

//    private fun generateDataClass(factoryElement: TypeElement, writer: BufferedWriter?, className: String, packageName: String, creationMethod: ExecutableElement) {
//        val cu = CompilationUnit();
//        val cuBuilder = CompilationUnit();
//        // set the package
//        cu.setPackageDeclaration(packageName);
//
//        // create the type declaration
//        val type = cu.addClass(className, AstModifier.ABSTRACT)
//                    .addAnnotation(AutoValue::class.java)
//        factoryElement.annotationMirrors
//                .union(creationMethod.annotationMirrors)
//                .find { it.toString().contains("Parcelable") }?.let {
//            type.addImplementedType("android.os.Parcelable")
//        }
//        type.tryAddImportToParentCompilationUnit(AutoValue.Builder::class.java)
//
//        val builderType = cuBuilder.addClass("Builder", AstModifier.ABSTRACT, AstModifier.STATIC)
//                            .addAnnotation(AutoValue.Builder::class.java.canonicalName)
//        type.addMember(builderType)
//
//        var newTypeAdapterStmt:Expression? = null
//        // add gson adapter
//        val gson = (factoryElement.getAnnotation(Gson::class.java)
//                ?: (creationMethod.getAnnotation(Gson::class.java)));
//
//        gson?.let {
//            if (it.value) {
//                newTypeAdapterStmt = ObjectCreationExpr()
//                        .setType("AutoValue_$className.GsonTypeAdapter")
//                        .addArgument("gson")
//            }
//        }
//
//        val builderMethod = type.addMethod("builder", AstModifier.STATIC)
//                                .setType("Builder")
//
//        val builderBody = BlockStmt()
//        val newOperation = ObjectCreationExpr().setType("AutoValue_$className.Builder")
//
//        var builderCall:Expression = newOperation
//
//        creationMethod.parameters.forEach {
//            val propertyName = it.simpleName.toString()
//            // create a method
//
//            // Create Getter
//            val propertyGetter = type.addMethod(propertyName, AstModifier.PUBLIC, AstModifier.ABSTRACT)
//                    .setType(it.asType().toString())
//                    .removeBody()
//
//            // Create Setter
//            val propertySetter = builderType.addMethod(propertyName, AstModifier.PUBLIC, AstModifier.ABSTRACT)
//                    .addParameter(it.asType().toString(), propertyName)
//                    .setType("Builder")
//                    .removeBody()
//
//            //Name annotations
//            it.getAnnotation(Named::class.java)?.let {
//                propertyGetter.addAndGetAnnotation(SerializedName::class.java)
//                        .addPair("value", "\"" + it.value + "\"")
//            }
//
//            //set defaults
//            // ints
//            it.getAnnotation(DefaultInt::class.java)?.let { def ->
//                builderCall = MethodCallExpr(builderCall, propertyName)
//                        .addArgument(IntegerLiteralExpr(def.value))
//                newTypeAdapterStmt = newTypeAdapterStmt?.let {
//                    MethodCallExpr(it, "setDefault" + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1))
//                            .addArgument(IntegerLiteralExpr(def.value))
//                }
//            }
//
//            // longs
//            it.getAnnotation(DefaultLong::class.java)?.let { def ->
//                builderCall = MethodCallExpr(builderCall, propertyName)
//                        .addArgument(LongLiteralExpr(def.value))
//                newTypeAdapterStmt = newTypeAdapterStmt?.let {
//                    MethodCallExpr(it, "setDefault" + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1))
//                            .addArgument(LongLiteralExpr(def.value))
//                }
//            }
//
//            // short
//            it.getAnnotation(DefaultShort::class.java)?.let { def ->
//                builderCall = MethodCallExpr(builderCall, propertyName)
//                        .addArgument("(short)"+def.value.toString())
//                newTypeAdapterStmt = newTypeAdapterStmt?.let {
//                    MethodCallExpr(it, "setDefault" + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1))
//                            .addArgument("(short)"+def.value.toString())
//                }
//            }
//
//            // Byte
//            it.getAnnotation(DefaultByte::class.java)?.let { def ->
//                builderCall = MethodCallExpr(builderCall, propertyName)
//                        .addArgument("(byte)"+def.value.toString())
//                newTypeAdapterStmt = newTypeAdapterStmt?.let {
//                    MethodCallExpr(it, "setDefault" + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1))
//                            .addArgument("(byte)"+def.value.toString())
//                }
//            }
//
//            // Boolean
//            it.getAnnotation(DefaultBool::class.java)?.let { def ->
//                builderCall = MethodCallExpr(builderCall, propertyName)
//                        .addArgument(BooleanLiteralExpr(def.value))
//                newTypeAdapterStmt = newTypeAdapterStmt?.let {
//                    MethodCallExpr(it, "setDefault" + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1))
//                            .addArgument(BooleanLiteralExpr(def.value))
//                }
//            }
//
//            // Double
//            it.getAnnotation(DefaultDouble::class.java)?.let { def ->
//                builderCall = MethodCallExpr(builderCall, propertyName)
//                        .addArgument(DoubleLiteralExpr(def.value))
//                newTypeAdapterStmt = newTypeAdapterStmt?.let {
//                    MethodCallExpr(it, "setDefault" + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1))
//                            .addArgument(DoubleLiteralExpr(def.value))
//                }
//            }
//
//            // Float
//            it.getAnnotation(DefaultFloat::class.java)?.let { def ->
//                builderCall = MethodCallExpr(builderCall, propertyName)
//                        .addArgument(def.value.toString()+"f")
//                newTypeAdapterStmt = newTypeAdapterStmt?.let {
//                    MethodCallExpr(it, "setDefault" + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1))
//                            .addArgument(def.value.toString()+"f")
//                }
//            }
//
//            //Strings
//            it.getAnnotation(DefaultString::class.java)?.let { def ->
//                builderCall = MethodCallExpr(builderCall, propertyName)
//                        .addArgument(StringLiteralExpr(def.value))
//                newTypeAdapterStmt = newTypeAdapterStmt?.let {
//                    MethodCallExpr(it, "setDefault" + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1))
//                            .addArgument(StringLiteralExpr(def.value))
//                }
//            }
//
//            //def values for primary types
//            it.asType().kind.let { kind ->
//                if (kind.isPrimitive && it.annotationMirrors.isEmpty()) {
//                    val defaultMethod = MethodCallExpr(builderCall, propertyName)
//                    when(kind) {
//                        TypeKind.INT -> "0"
//                        TypeKind.BOOLEAN -> "false"
//                        TypeKind.BYTE -> "(byte)0"
//                        TypeKind.SHORT -> "(short)0"
//                        TypeKind.LONG -> "0l"
//                        TypeKind.CHAR -> "0"
//                        TypeKind.FLOAT -> "0.0f"
//                        TypeKind.DOUBLE -> "0.0d"
//                        else -> ""
//                    }.takeIf { !it.isEmpty() }?.let (defaultMethod::addArgument)
//                    builderCall = defaultMethod
//                }
//            }
//
//            //Nullables
//            if (!it.asType().kind.isPrimitive) {
//                val nullableAnnotated =
//                        (factoryElement.annotationMirrors
//                            .union(creationMethod.annotationMirrors)
//                            .any { it.toString().contains("Nullable") }
//                            && !it.annotationMirrors
//                                .any { it.toString().toLowerCase().contains("nonnull") })
//                        || it.annotationMirrors
//                            .any { it.toString().contains("Nullable") }
//
//                if (nullableAnnotated) {
//                    propertyGetter.addAnnotation(javax.annotation.Nullable::class.java)
//                    propertySetter.parameters.forEach { it.addAnnotation(javax.annotation.Nullable::class.java) }
//                } else {
//                    propertyGetter.addAnnotation(javax.annotation.Nonnull::class.java)
//                    propertySetter.parameters.forEach { it.addAnnotation(javax.annotation.Nonnull::class.java) }
//                }
//            }
//        }
//
//        val returnStm = ReturnStmt(builderCall)
//        builderBody.addStatement(returnStm)
//        builderMethod.setBody(builderBody)
//
//        // gson part 2
//        gson?.let {
//            val gsonTypeAdapter = type.addMethod("typeAdapter", AstModifier.PUBLIC, AstModifier.STATIC)
//                    .setType(TypeAdapter::class.java.canonicalName+"<"+className+">")
//                    .addParameter(com.google.gson.Gson::class.java, "gson")
//
//            val typeAdapterBody = BlockStmt()
//
//            if (newTypeAdapterStmt == null) {
//                newTypeAdapterStmt = ObjectCreationExpr()
//                        .setType("AutoValue_$className.GsonTypeAdapter")
//                        .addArgument("gson")
//            }
//
//            val typeAdapterReturn = ReturnStmt(newTypeAdapterStmt)
//
//            typeAdapterBody.addStatement(typeAdapterReturn)
//            gsonTypeAdapter.setBody(typeAdapterBody)
//        }
//
//        // Add build method
//        builderType.addMethod("build", AstModifier.PUBLIC, AstModifier.ABSTRACT)
//                .setType(className)
//                .removeBody()
//
//        // toBuilder
//        type.addMethod("toBuilder", AstModifier.PUBLIC, AstModifier.ABSTRACT)
//                .setType("Builder")
//                .removeBody()
//
//        writer?.run {
//            write(cu.toString())
//            flush()
//        }
//    }

    private fun error(e: Element, msg: String, vararg args: Any) {
        messager.printMessage(
                Diagnostic.Kind.ERROR,
                String.format(msg, *args),
                e)
    }

    private fun info(e: Element, msg: String, vararg args: Any) {
        messager.printMessage(
                Diagnostic.Kind.WARNING,
                String.format(msg, *args),
                e)
    }


    companion object {
        private var supportedAnnotations = HashSet<String>()
        private var supportedAnnotationsClasses = mutableListOf<Class<out Annotation>>()

        init {
            supportedAnnotationsClasses.apply {
                add(DefaultInt::class.java)
                add(DefaultLong::class.java)
                add(DefaultShort::class.java)
                add(DefaultByte::class.java)
                add(DefaultBool::class.java)
                add(DefaultFloat::class.java)
                add(DefaultDouble::class.java)
                add(DefaultString::class.java)
                add(Default::class.java)
            }.forEach {
                supportedAnnotations.add(it.canonicalName)
            }
        }
    }
}