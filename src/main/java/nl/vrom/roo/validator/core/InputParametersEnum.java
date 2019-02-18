package nl.vrom.roo.validator.core;

/*	ROO zaken vervangen door strings, indien validator hierover valt dan verder in duiken
	De Lange, R, 02052014

	GELEIDEFORMULIER(GeleideFormulier.class.getName()), -- vervangen door --> 	GELEIDEFORMULIER("GeleideFormulier"),
	ANIFEST(Manifest.class.getName()), 				-- vervangen door --> 	MANIFEST("Manifest"),
	PLANDOSSIER(PlanDossier.class.getName()); 			-- vervangen door --> 	PLANDOSSIER("PlanDossier");
*/

public enum InputParametersEnum {

	GELEIDEFORMULIER("GeleideFormulier"),
	MANIFEST("Manifest"),
//	PLANTEKST,		// To help future refactoring
//	PLANFILE,		// To help future refactoring
	PLANDOSSIER("PlanDossier");

	private String name;
	
	private InputParametersEnum() {
		this.name = name();
	}
	
	private InputParametersEnum(String name) {
		this.name = name; 
	}
	
	public String getName() {
		return name;
	}
}