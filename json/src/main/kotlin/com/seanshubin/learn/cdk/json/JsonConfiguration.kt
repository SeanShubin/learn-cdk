package com.seanshubin.learn.cdk.json

import com.fasterxml.jackson.module.kotlin.readValue
import java.lang.ClassCastException

object JsonConfiguration {
    fun put(destination:String, value:Any?, path:List<String>):String {
        val model = JsonMappers.parser.readValue<Map<String, Any?>>(destination)
        putObject(model, value, path)
        return JsonMappers.pretty.writeValueAsString(model)
    }
    private fun putObject(untypedDestination:Any?, value:Any?, path:List<String>):Map<String, Any?> {
        val destination = castToJsonObject(untypedDestination, path)
        val head = path[0]
        val tail = path.drop(1)
        return if(path.size == 1){
            destination + (head to value)
        } else {
            val existing = destination[head]
            if(existing == null){
                destination + (head to putObject(emptyMap<String, Any?>(), value, tail))
            } else {
                destination + (head to putObject(existing, value, tail))
            }
        }
    }

    private fun getObject(untypedSource:Any?, default:Any?,  path:List<String>):Any? {
        val source = castToJsonObject(untypedSource, path)
        val head = path[0]
        val tail = path.drop(1)
        return if(path.size == 1){
            if(source.containsKey(head)){
                source[head]
            } else {
                default
            }
        } else {
            val existing = source[head]
            if(existing == null){
                default
            } else {
                getObject(existing, default, tail)
            }
        }
    }

    private fun castToString(value:Any?, path:List<String>):String =
        when(value){
            null -> throwCastError(null, "null", "String", path)
            is String ->  value
            else -> throwCastError(value, value.javaClass.simpleName,"String", path)
        }

    private fun castToInt(value:Any?, path:List<String>):Int =
        when(value){
            null -> throwCastError(null, "null", "Int", path)
            is Int ->  value
            else -> throwCastError(value, value.javaClass.simpleName,"Int", path)
        }

    @Suppress("UNCHECKED_CAST")
    private fun castToJsonObject(value:Any?, path:List<String>):Map<String, Any?> =
        when(value){
            null -> throwCastError(null, "null", "Map<String, Object>",path)
            is Map<*,*> -> value as Map<String, Any?>
            else -> throwCastError(value, value.javaClass.simpleName, "Map<String, Object>", path)
        }

    private fun throwCastError(value:Any?,
                               sourceTypeName:String,
                               destinationTypeName:String,
                               path:List<String>):Nothing {
        val joinedPath = path.joinToString(".")
        val message = "Unable to cast value $value of type $sourceTypeName to $destinationTypeName at path $joinedPath"
        throw ClassCastException(message)
    }
}