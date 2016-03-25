#!/usr/bin/env groovy

package org.dict_uk.expand


import groovy.transform.TypeChecked;
import java.util.regex.*;

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

import org.dict_uk.expand.Expand

class ExpandComps {
	static Logger log = LogManager.getFormatterLogger(ExpandComps.class);

	final Pattern tags_re = Pattern.compile("(.*:)[mfnp]:v_...(.*)")
	final Pattern gen_vidm_pattern = Pattern.compile(":(.:v_...(:r(in)?anim)?)")
	final Expand expand

//	static {
//		String.metaClass.isCase = { delegate.contains(it) }	// pythonize
//		assert "b" in "abc"
//	}

	public ExpandComps(Expand expand) {
		this.expand = expand
	}

	@TypeChecked
	List<String> match_comps(List<String> lefts, List<String> rights) {
		def outs = []
		Map<String, List<String>> left_v = [:]
		def left_gen = ""
		def mixed_gen = false


		def left_wn
		def left_tags


		for( ln in lefts ) {
			def parts = ln.split(" ")
			def rrr = gen_vidm_pattern.matcher(parts[2])
			if( ! rrr.find() ) {
				log.warn("ignoring left %s", ln)
				continue
			}

			def vidm = rrr.group(1)
			if( "mfn".contains(vidm[0]) ) {
				if( !left_gen )
					left_gen = vidm[0]
				else
				if( left_gen != vidm[0] )
					mixed_gen = true
			}
			if( ! (vidm in left_v) )
				left_v[vidm] = []

			left_v[vidm].add(parts[0])
			left_wn = parts[1]
			left_tags = parts[2]
		}
		if( mixed_gen ) {
			left_gen = ""
			//     print("left_gen", left_gen, "mixed_gen", mixed_gen, file=sys.stderr)
		}


		for( rn in rights ) {
			def parts = rn.split(" ")
			def rrr = gen_vidm_pattern.matcher(rn)
			if( ! rrr.find() ) {
				log.warn("composite: ignoring right %s", rn)
				continue
			}

			def vidm = rrr.group(1)
			if( left_gen != "" && "mfn".contains(vidm[0]) )
				vidm = left_gen + vidm[1..-1]

			if( !(vidm in left_v) )
				continue

			for(String left_wi in left_v[vidm] ) {
				String w_infl = left_wi + "-" + parts[0]
				String lemma = left_wn + "-" + parts[1]
				
				def str = w_infl + " " + lemma + " " +
						tags_re.matcher(left_tags).replaceAll('$1'+vidm+'$2')
				outs.add(str)
			}
		}

		return outs
	}

	@TypeChecked
	List<String> expand_composite_line(String line) {
		if( ! line.contains(" - ") )
			return [line]

		def parts_all
		if( line.contains(" :") ) {
			parts_all = line.split(" :")
			line = parts_all[0]
			parts_all[1] = ":" + parts_all[1]
		}
		else if( line.contains(" ^") ) {
			parts_all = line.split(/ \^/)
			line = parts_all[0]
			parts_all[1] = "^" + parts_all[1]
		}
		else {
			parts_all = [line]
		}

		String[] parts = line.split(" - ")
		//        print(parts, file=sys.stderr)

		if( ! parts[1].contains("/") )
			parts[1] += " noun:m:nv"

		if( parts_all.size() > 1 ) {
			def extra_tags = parts_all[1]
			parts[0] += " " + extra_tags
			parts[1] += " " + extra_tags
		}

		def lefts = expand.expand_line(parts[0]) //, true)

		def rights = expand.expand_line(parts[1]) //, true)

		def comps = match_comps(lefts, rights)

		return comps
	}

	@TypeChecked
	def process_input(List<String> out_lines) {
		def out = []

		for( line in out_lines ) {

			line = line.trim()
			if( ! line || line[0] == "#" )
				continue

			try {
				def comps = expand_composite_line(line)
				
				if( Character.isUpperCase(line.charAt(0)) && ! line.contains("<") ) {
				    comps = comps.collect { it + ":prop" }
				}
				
				out.addAll(comps)
			}
			catch(e) {
				System.err.printf("Failed at %s\n", line)
				throw e
			}
		}

		return out
	}

}
